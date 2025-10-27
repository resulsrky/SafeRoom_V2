package com.saferoom.p2p;

import com.saferoom.natghost.LLS;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelState;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * Reliable Messaging over WebRTC DataChannel
 * 
 * Adapts the existing LLS reliable messaging protocol (ReliableMessageSender/Receiver)
 * to work over WebRTC DataChannel instead of UDP.
 * 
 * Features:
 * - Chunking (1131 bytes/chunk, MTU-safe)
 * - Selective ACK with 64-bit bitmap
 * - NACK for fast retransmission
 * - CRC32 validation
 * - RTT estimation
 * 
 * Protocol: Uses LLS packet format (SIG_RMSG_DATA, SIG_RMSG_ACK, SIG_RMSG_NACK, SIG_RMSG_FIN)
 */
public class DataChannelReliableMessaging {
    
    // Constants from ReliableMessageSender
    private static final int MAX_CHUNK_SIZE = 1131;
    private static final int INITIAL_RTO_MS = 1000;
    private static final int MAX_RETRIES = 5;
    private static final int ACK_DELAY_MS = 50;
    
    // Message ID generator
    private static final AtomicLong messageIdGenerator = new AtomicLong(System.currentTimeMillis());
    
    private final String username;
    private final RTCDataChannel dataChannel;
    private final ScheduledExecutorService scheduler;
    
    // Send state
    private final ConcurrentHashMap<Long, SendState> sendingMessages = new ConcurrentHashMap<>();
    
    // Receive state
    private final ConcurrentHashMap<Long, ReceiveState> receivingMessages = new ConcurrentHashMap<>();
    
    // Callback for completed messages
    private MessageCompletionCallback completionCallback;
    
    @FunctionalInterface
    public interface MessageCompletionCallback {
        void onMessageComplete(String sender, long messageId, byte[] message);
    }
    
    public DataChannelReliableMessaging(String username, RTCDataChannel dataChannel) {
        this.username = username;
        this.dataChannel = dataChannel;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "DCReliable-" + username);
            t.setDaemon(true);
            return t;
        });
        
        startRetryTimer();
    }
    
    public void setCompletionCallback(MessageCompletionCallback callback) {
        this.completionCallback = callback;
    }
    
    /**
     * Send message reliably over DataChannel
     */
    public CompletableFuture<Boolean> sendMessage(String receiver, String messageText) {
        try {
            byte[] messageBytes = messageText.getBytes("UTF-8");
            long messageId = messageIdGenerator.incrementAndGet();
            
            SendState state = new SendState(messageId, receiver, messageBytes);
            sendingMessages.put(messageId, state);
            
            // Send all chunks
            state.state = SendState.State.SENDING;
            for (ChunkInfo chunk : state.chunks) {
                sendChunk(state, chunk);
            }
            
            state.state = SendState.State.WAITING_ACK;
            return state.completionFuture;
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error sending message: %s%n", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Handle incoming DataChannel message
     */
    public void handleIncomingMessage(RTCDataChannelBuffer buffer) {
        try {
            ByteBuffer data = buffer.data.duplicate();
            if (data.remaining() < 1) return;
            
            byte signalType = data.get();
            
            switch (signalType) {
                case LLS.SIG_RMSG_DATA:
                    handleDataChunk(data);
                    break;
                case LLS.SIG_RMSG_ACK:
                    handleAck(data);
                    break;
                case LLS.SIG_RMSG_NACK:
                    handleNack(data);
                    break;
                case LLS.SIG_RMSG_FIN:
                    handleFin(data);
                    break;
                default:
                    System.err.printf("[DCReliable] Unknown signal type: 0x%02X%n", signalType);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error handling message: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Send a single chunk
     */
    private void sendChunk(SendState state, ChunkInfo chunk) {
        try {
            if (dataChannel.getState() != RTCDataChannelState.OPEN) {
                System.err.println("[DCReliable] DataChannel not open!");
                return;
            }
            
            // Build LLS SIG_RMSG_DATA packet
            // Format: signal(1) + messageId(8) + chunkId(4) + totalChunks(4) + crc(4) + data(N)
            int packetSize = 1 + 8 + 4 + 4 + 4 + chunk.length;
            ByteBuffer packet = ByteBuffer.allocate(packetSize);
            
            packet.put(LLS.SIG_RMSG_DATA);
            packet.putLong(state.messageId);
            packet.putInt(chunk.chunkId);
            packet.putInt(state.chunks.size());
            
            // Calculate CRC32
            byte[] chunkData = new byte[chunk.length];
            System.arraycopy(state.fullMessage, chunk.offset, chunkData, 0, chunk.length);
            CRC32 crc = new CRC32();
            crc.update(chunkData);
            packet.putInt((int) crc.getValue());
            
            // Add data
            packet.put(chunkData);
            
            packet.flip();
            RTCDataChannelBuffer dcBuffer = new RTCDataChannelBuffer(packet, true);
            dataChannel.send(dcBuffer);
            
            chunk.lastSentTime = System.currentTimeMillis();
            chunk.retries++;
            state.inFlightChunks.set(chunk.chunkId);
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error sending chunk: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle incoming data chunk
     */
    private void handleDataChunk(ByteBuffer data) {
        try {
            long messageId = data.getLong();
            int chunkId = data.getInt();
            int totalChunks = data.getInt();
            int crc = data.getInt();
            
            byte[] chunkData = new byte[data.remaining()];
            data.get(chunkData);
            
            // Validate CRC
            CRC32 crcCalc = new CRC32();
            crcCalc.update(chunkData);
            if ((int) crcCalc.getValue() != crc) {
                System.err.printf("[DCReliable] CRC mismatch for chunk %d of message %d%n", chunkId, messageId);
                sendNack(messageId, chunkId);
                return;
            }
            
            // Get or create receive state
            ReceiveState state = receivingMessages.computeIfAbsent(messageId, 
                id -> new ReceiveState(messageId, totalChunks));
            
            // Store chunk
            state.receivedChunks.put(chunkId, chunkData);
            state.receivedBitmap.set(chunkId);
            state.lastChunkTime = System.currentTimeMillis();
            
            System.out.printf("[DCReliable] Received chunk %d/%d of message %d%n", 
                chunkId + 1, totalChunks, messageId);
            
            // Schedule ACK
            scheduler.schedule(() -> sendAck(state), ACK_DELAY_MS, TimeUnit.MILLISECONDS);
            
            // Check if complete
            if (state.isComplete()) {
                completeMessage(state);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error handling data chunk: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle ACK
     */
    private void handleAck(ByteBuffer data) {
        try {
            long messageId = data.getLong();
            long bitmap = data.getLong();
            
            SendState state = sendingMessages.get(messageId);
            if (state == null) return;
            
            // Mark chunks as ACKed based on bitmap
            for (int i = 0; i < 64 && i < state.chunks.size(); i++) {
                if ((bitmap & (1L << i)) != 0) {
                    state.ackedChunks.set(i);
                }
            }
            
            System.out.printf("[DCReliable] ACK for message %d: %d/%d chunks%n", 
                messageId, state.ackedChunks.cardinality(), state.chunks.size());
            
            // Check if complete
            if (state.isComplete()) {
                state.state = SendState.State.COMPLETE;
                state.completionFuture.complete(true);
                sendingMessages.remove(messageId);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error handling ACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle NACK
     */
    private void handleNack(ByteBuffer data) {
        try {
            long messageId = data.getLong();
            int chunkId = data.getInt();
            
            SendState state = sendingMessages.get(messageId);
            if (state == null) return;
            
            System.out.printf("[DCReliable] NACK for message %d, chunk %d - retransmitting%n", 
                messageId, chunkId);
            
            // Retransmit requested chunk
            if (chunkId < state.chunks.size()) {
                ChunkInfo chunk = state.chunks.get(chunkId);
                sendChunk(state, chunk);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error handling NACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle FIN
     */
    private void handleFin(ByteBuffer data) {
        try {
            long messageId = data.getLong();
            
            ReceiveState state = receivingMessages.get(messageId);
            if (state != null && state.isComplete()) {
                completeMessage(state);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error handling FIN: %s%n", e.getMessage());
        }
    }
    
    /**
     * Send ACK
     */
    private void sendAck(ReceiveState state) {
        try {
            if (dataChannel.getState() != RTCDataChannelState.OPEN) return;
            
            // Format: signal(1) + messageId(8) + bitmap(8)
            ByteBuffer packet = ByteBuffer.allocate(17);
            packet.put(LLS.SIG_RMSG_ACK);
            packet.putLong(state.messageId);
            packet.putLong(state.getBitmap());
            
            packet.flip();
            RTCDataChannelBuffer dcBuffer = new RTCDataChannelBuffer(packet, true);
            dataChannel.send(dcBuffer);
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error sending ACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Send NACK
     */
    private void sendNack(long messageId, int chunkId) {
        try {
            if (dataChannel.getState() != RTCDataChannelState.OPEN) return;
            
            // Format: signal(1) + messageId(8) + chunkId(4)
            ByteBuffer packet = ByteBuffer.allocate(13);
            packet.put(LLS.SIG_RMSG_NACK);
            packet.putLong(messageId);
            packet.putInt(chunkId);
            
            packet.flip();
            RTCDataChannelBuffer dcBuffer = new RTCDataChannelBuffer(packet, true);
            dataChannel.send(dcBuffer);
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error sending NACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Complete message reception
     */
    private void completeMessage(ReceiveState state) {
        try {
            byte[] fullMessage = state.reassemble();
            String messageText = new String(fullMessage, "UTF-8");
            
            System.out.printf("[DCReliable] ✅ Message %d complete: %d bytes%n", 
                state.messageId, fullMessage.length);
            
            receivingMessages.remove(state.messageId);
            
            if (completionCallback != null) {
                completionCallback.onMessageComplete(username, state.messageId, fullMessage);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCReliable] Error completing message: %s%n", e.getMessage());
        }
    }
    
    /**
     * Retry timer for retransmissions
     */
    private void startRetryTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                
                for (SendState state : sendingMessages.values()) {
                    if (state.state == SendState.State.WAITING_ACK) {
                        // Retransmit unacked chunks
                        for (ChunkInfo chunk : state.chunks) {
                            if (!state.ackedChunks.get(chunk.chunkId) && 
                                now - chunk.lastSentTime > INITIAL_RTO_MS) {
                                
                                if (chunk.retries >= MAX_RETRIES) {
                                    state.state = SendState.State.FAILED;
                                    state.completionFuture.complete(false);
                                    sendingMessages.remove(state.messageId);
                                    break;
                                }
                                
                                sendChunk(state, chunk);
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                System.err.printf("[DCReliable] Error in retry timer: %s%n", e.getMessage());
            }
        }, INITIAL_RTO_MS, INITIAL_RTO_MS / 2, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Send state
     */
    private static class SendState {
        enum State { IDLE, SENDING, WAITING_ACK, RETRANSMITTING, COMPLETE, FAILED }
        
        final long messageId;
        final String receiver;
        final byte[] fullMessage;
        final List<ChunkInfo> chunks;
        final BitSet ackedChunks;
        final BitSet inFlightChunks;
        
        State state;
        CompletableFuture<Boolean> completionFuture;
        
        SendState(long messageId, String receiver, byte[] message) {
            this.messageId = messageId;
            this.receiver = receiver;
            this.fullMessage = message;
            
            int totalChunks = (message.length + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE;
            this.chunks = new ArrayList<>(totalChunks);
            
            for (int i = 0; i < totalChunks; i++) {
                int offset = i * MAX_CHUNK_SIZE;
                int length = Math.min(MAX_CHUNK_SIZE, message.length - offset);
                chunks.add(new ChunkInfo(i, offset, length));
            }
            
            this.ackedChunks = new BitSet(totalChunks);
            this.inFlightChunks = new BitSet(totalChunks);
            this.state = State.IDLE;
            this.completionFuture = new CompletableFuture<>();
        }
        
        boolean isComplete() {
            return ackedChunks.cardinality() == chunks.size();
        }
    }
    
    /**
     * Receive state
     */
    private static class ReceiveState {
        final long messageId;
        final int totalChunks;
        final Map<Integer, byte[]> receivedChunks;
        final BitSet receivedBitmap;
        long lastChunkTime;
        
        ReceiveState(long messageId, int totalChunks) {
            this.messageId = messageId;
            this.totalChunks = totalChunks;
            this.receivedChunks = new ConcurrentHashMap<>();
            this.receivedBitmap = new BitSet(totalChunks);
            this.lastChunkTime = System.currentTimeMillis();
        }
        
        boolean isComplete() {
            return receivedChunks.size() == totalChunks;
        }
        
        long getBitmap() {
            long bitmap = 0;
            for (int i = 0; i < Math.min(64, totalChunks); i++) {
                if (receivedChunks.containsKey(i)) {
                    bitmap |= (1L << i);
                }
            }
            return bitmap;
        }
        
        byte[] reassemble() {
            int totalSize = receivedChunks.values().stream()
                .mapToInt(arr -> arr.length)
                .sum();
            
            byte[] fullMessage = new byte[totalSize];
            int offset = 0;
            
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = receivedChunks.get(i);
                if (chunk == null) {
                    throw new IllegalStateException("Missing chunk " + i);
                }
                System.arraycopy(chunk, 0, fullMessage, offset, chunk.length);
                offset += chunk.length;
            }
            
            return fullMessage;
        }
    }
    
    /**
     * Chunk info
     */
    private static class ChunkInfo {
        final int chunkId;
        final int offset;
        final int length;
        int retries;
        long lastSentTime;
        
        ChunkInfo(int chunkId, int offset, int length) {
            this.chunkId = chunkId;
            this.offset = offset;
            this.length = length;
            this.retries = 0;
            this.lastSentTime = 0;
        }
    }
    
    public void shutdown() {
        scheduler.shutdownNow();
        sendingMessages.clear();
        receivingMessages.clear();
    }
}
