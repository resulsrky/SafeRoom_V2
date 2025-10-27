package com.saferoom.p2p;

import com.saferoom.file_transfer.*;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32C;

/**
 * File Transfer over WebRTC DataChannel
 * 
 * Adapts the existing high-performance file transfer protocol to DataChannel:
 * - 3-way handshake (SYN → ACK → SYN_ACK)
 * - ChunkManager for unlimited file size
 * - CRC32C validation
 * - NACK-based retransmission
 * - Congestion control
 * 
 * Protocol:
 * 1. Sender: SYN (fileId, size, totalChunks)
 * 2. Receiver: ACK
 * 3. Sender: SYN_ACK
 * 4. Sender: DATA chunks (with CRC)
 * 5. Receiver: NACK for missing chunks
 * 6. Sender: Retransmit missing chunks
 */
public class DataChannelFileTransfer {
    
    // Protocol constants (from file_transfer package)
    private static final int SLICE_SIZE = 1450;  // MTU-safe chunk size
    private static final int HANDSHAKE_HEADER_SIZE = 21;  // HandShake_Packet: signal(1) + fileId(8) + size(8) + totalSeq(4)
    private static final int DATA_HEADER_SIZE = 23;       // DATA packet: signal(1) + fileId(8) + seqNo(4) + total(4) + len(4) + crc(4)
    private static final int PACKET_SIZE = SLICE_SIZE + DATA_HEADER_SIZE;
    
    // Handshake signals
    private static final byte SYN = 0x01;
    private static final byte ACK = 0x10;
    private static final byte SYN_ACK = 0x11;
    private static final byte DATA = 0x00;
    private static final byte NACK = 0x02;
    private static final byte FIN = 0x03;
    
    private final String username;
    private final RTCDataChannel dataChannel;
    private final ScheduledExecutorService scheduler;
    
    // Send state
    private final Map<Long, SendState> sendingFiles = new ConcurrentHashMap<>();
    
    // Receive state
    private final Map<Long, ReceiveState> receivingFiles = new ConcurrentHashMap<>();
    
    // Callbacks
    private FileTransferCallback transferCallback;
    
    @FunctionalInterface
    public interface FileTransferCallback {
        void onFileReceived(String sender, long fileId, Path filePath, long fileSize);
    }
    
    public DataChannelFileTransfer(String username, RTCDataChannel dataChannel) {
        this.username = username;
        this.dataChannel = dataChannel;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "DCFileTransfer-" + username);
            t.setDaemon(true);
            return t;
        });
    }
    
    public void setTransferCallback(FileTransferCallback callback) {
        this.transferCallback = callback;
    }
    
    /**
     * Send file via DataChannel
     */
    public CompletableFuture<Boolean> sendFile(String receiver, Path filePath) {
        try {
            long fileId = System.currentTimeMillis();
            long fileSize = filePath.toFile().length();
            int totalChunks = (int) ((fileSize + SLICE_SIZE - 1) / SLICE_SIZE);
            
            System.out.printf("[DCFileTransfer] 📤 Sending file: %s (%d bytes, %d chunks)%n",
                filePath.getFileName(), fileSize, totalChunks);
            
            SendState state = new SendState(fileId, receiver, filePath, fileSize, totalChunks);
            sendingFiles.put(fileId, state);
            
            // Start handshake
            return performHandshake(state);
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error initiating file send: %s%n", e.getMessage());
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
            
            byte signal = data.get(0);
            
            switch (signal) {
                case SYN:
                    handleSyn(data);
                    break;
                case ACK:
                    handleAck(data);
                    break;
                case SYN_ACK:
                    handleSynAck(data);
                    break;
                case DATA:
                    handleDataChunk(data);
                    break;
                case NACK:
                    handleNack(data);
                    break;
                case FIN:
                    handleFin(data);
                    break;
                default:
                    System.err.printf("[DCFileTransfer] Unknown signal: 0x%02X%n", signal);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling message: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Perform 3-way handshake
     */
    private CompletableFuture<Boolean> performHandshake(SendState state) {
        try {
            // Send SYN (HandShake_Packet format)
            ByteBuffer syn = ByteBuffer.allocate(HANDSHAKE_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
            syn.put(SYN);
            syn.putLong(state.fileId);
            syn.putLong(state.fileSize);
            syn.putInt(state.totalChunks);
            syn.flip();
            
            sendDataChannelMessage(syn);
            System.out.printf("[DCFileTransfer] 🤝 SYN sent for fileId=%d%n", state.fileId);
            
            state.state = SendState.State.WAITING_ACK;
            
            // Schedule timeout
            scheduler.schedule(() -> {
                if (state.state == SendState.State.WAITING_ACK) {
                    System.err.printf("[DCFileTransfer] ❌ Handshake timeout for fileId=%d%n", state.fileId);
                    state.completionFuture.complete(false);
                    sendingFiles.remove(state.fileId);
                }
            }, 30, TimeUnit.SECONDS);
            
            return state.completionFuture;
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Handshake error: %s%n", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Handle SYN (receiver side)
     */
    private void handleSyn(ByteBuffer data) {
        try {
            data.order(ByteOrder.BIG_ENDIAN);
            data.position(1); // Skip signal byte
            
            long fileId = data.getLong();
            long fileSize = data.getLong();
            int totalChunks = data.getInt();
            
            System.out.printf("[DCFileTransfer] 📥 SYN received: fileId=%d, size=%d, chunks=%d%n",
                fileId, fileSize, totalChunks);
            
            // Create receive state
            String fileName = "received_file_" + fileId;
            Path filePath = Path.of("downloads", fileName);
            filePath.getParent().toFile().mkdirs();
            
            ReceiveState state = new ReceiveState(fileId, filePath, fileSize, totalChunks);
            receivingFiles.put(fileId, state);
            
            // Send ACK (HandShake_Packet format)
            ByteBuffer ack = ByteBuffer.allocate(HANDSHAKE_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
            ack.put(ACK);
            ack.putLong(fileId);
            ack.putLong(fileSize);
            ack.putInt(totalChunks);
            ack.flip();
            
            sendDataChannelMessage(ack);
            System.out.printf("[DCFileTransfer] ✅ ACK sent for fileId=%d%n", fileId);
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling SYN: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle ACK (sender side)
     */
    private void handleAck(ByteBuffer data) {
        try {
            data.order(ByteOrder.BIG_ENDIAN);
            data.position(1);
            
            long fileId = data.getLong();
            
            SendState state = sendingFiles.get(fileId);
            if (state == null) return;
            
            System.out.printf("[DCFileTransfer] ✅ ACK received for fileId=%d%n", fileId);
            
            // Send SYN_ACK (HandShake_Packet format)
            ByteBuffer synAck = ByteBuffer.allocate(HANDSHAKE_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
            synAck.put(SYN_ACK);
            synAck.putLong(fileId);
            synAck.flip();
            
            sendDataChannelMessage(synAck);
            System.out.printf("[DCFileTransfer] ✅ SYN_ACK sent for fileId=%d%n", fileId);
            
            // Start sending file chunks
            state.state = SendState.State.SENDING;
            startSendingChunks(state);
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling ACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle SYN_ACK (receiver side)
     */
    private void handleSynAck(ByteBuffer data) {
        try {
            data.order(ByteOrder.BIG_ENDIAN);
            data.position(1);
            
            long fileId = data.getLong();
            
            ReceiveState state = receivingFiles.get(fileId);
            if (state == null) return;
            
            System.out.printf("[DCFileTransfer] ✅ SYN_ACK received for fileId=%d - ready to receive%n", fileId);
            
            state.state = ReceiveState.State.RECEIVING;
            state.transferStartTime = System.currentTimeMillis();
            
            // Initialize ChunkManager
            try {
                state.initializeChunkManager();
            } catch (IOException e) {
                System.err.printf("[DCFileTransfer] Failed to initialize ChunkManager: %s%n", e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling SYN_ACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Start sending file chunks
     */
    private void startSendingChunks(SendState state) {
        scheduler.execute(() -> {
            try {
                FileChannel fc = FileChannel.open(state.filePath, StandardOpenOption.READ);
                MappedByteBuffer mem = fc.map(FileChannel.MapMode.READ_ONLY, 0, state.fileSize);
                
                CRC32C crc = new CRC32C();
                
                for (int seqNo = 0; seqNo < state.totalChunks; seqNo++) {
                    if (dataChannel.getState() != RTCDataChannelState.OPEN) break;
                    
                    int offset = seqNo * SLICE_SIZE;
                    int take = (int) Math.min(SLICE_SIZE, state.fileSize - offset);
                    
                    // Zero-copy: duplicate + slice (like EnhancedFileTransferSender)
                    ByteBuffer payload = mem.duplicate();
                    payload.position(offset).limit(offset + take);
                    payload = payload.slice(); // Independent buffer
                    
                    // Calculate CRC on sliced buffer
                    crc.reset();
                    crc.update(payload.duplicate());
                    int crc32c = (int) crc.getValue();
                    
                    // Build DATA packet: signal(1) + fileId(8) + seqNo(4) + totalChunks(4) + length(4) + crc(4) + data(N)
                    // Total header: 25 bytes
                    ByteBuffer packet = ByteBuffer.allocate(25 + take).order(ByteOrder.BIG_ENDIAN);
                    packet.put(DATA);
                    packet.putLong(state.fileId);
                    packet.putInt(seqNo);
                    packet.putInt(state.totalChunks);
                    packet.putInt(take);
                    packet.putInt(crc32c);
                    
                    // Zero-copy: put from sliced buffer
                    payload.position(0).limit(take);
                    packet.put(payload);
                    packet.flip();
                    
                    sendDataChannelMessage(packet);
                    
                    state.sentChunks.set(seqNo);
                    
                    if (seqNo % 100 == 0) {
                        System.out.printf("[DCFileTransfer] 📤 Progress: %d/%d chunks%n", 
                            seqNo + 1, state.totalChunks);
                    }
                    
                    // Small delay to avoid flooding
                    Thread.sleep(1);
                }
                
                fc.close();
                
                state.state = SendState.State.WAITING_NACK;
                System.out.printf("[DCFileTransfer] ✅ All chunks sent for fileId=%d%n", state.fileId);
                
                // Send FIN after a delay
                scheduler.schedule(() -> sendFin(state.fileId), 500, TimeUnit.MILLISECONDS);
                
            } catch (Exception e) {
                System.err.printf("[DCFileTransfer] Error sending chunks: %s%n", e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Handle incoming data chunk (receiver side)
     */
    private void handleDataChunk(ByteBuffer data) {
        try {
            data.order(ByteOrder.BIG_ENDIAN);
            data.position(1);
            
            long fileId = data.getLong();
            int seqNo = data.getInt();
            int totalChunks = data.getInt();
            int length = data.getInt();
            int crc = data.getInt();
            
            byte[] chunkData = new byte[length];
            data.get(chunkData);
            
            // Validate CRC
            CRC32C crcCalc = new CRC32C();
            crcCalc.update(chunkData);
            if ((int) crcCalc.getValue() != crc) {
                System.err.printf("[DCFileTransfer] ❌ CRC mismatch for chunk %d of file %d%n", seqNo, fileId);
                sendNack(fileId, seqNo);
                return;
            }
            
            ReceiveState state = receivingFiles.get(fileId);
            if (state == null) return;
            
            // Write chunk
            state.writeChunk(seqNo, chunkData);
            
            if (seqNo % 100 == 0) {
                System.out.printf("[DCFileTransfer] 📥 Received chunk %d/%d%n", seqNo + 1, totalChunks);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling data chunk: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle NACK (sender side)
     */
    private void handleNack(ByteBuffer data) {
        try {
            data.order(ByteOrder.BIG_ENDIAN);
            data.position(1);
            
            long fileId = data.getLong();
            int seqNo = data.getInt();
            
            System.out.printf("[DCFileTransfer] 🔄 NACK received for fileId=%d, chunk=%d - retransmitting%n",
                fileId, seqNo);
            
            // TODO: Retransmit chunk
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling NACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Handle FIN (receiver side)
     */
    private void handleFin(ByteBuffer data) {
        try {
            data.order(ByteOrder.BIG_ENDIAN);
            data.position(1);
            
            long fileId = data.getLong();
            
            ReceiveState state = receivingFiles.get(fileId);
            if (state == null) return;
            
            System.out.printf("[DCFileTransfer] 🏁 FIN received for fileId=%d%n", fileId);
            
            // Check for missing chunks
            int[] missing = state.getMissingChunks();
            if (missing.length > 0) {
                System.out.printf("[DCFileTransfer] ⚠️ Missing %d chunks - requesting via NACK%n", missing.length);
                for (int chunkId : missing) {
                    sendNack(fileId, chunkId);
                }
            } else {
                completeFileReception(state);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error handling FIN: %s%n", e.getMessage());
        }
    }
    
    /**
     * Send NACK for missing chunk
     */
    private void sendNack(long fileId, int seqNo) {
        try {
            ByteBuffer nack = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
            nack.put(NACK);
            nack.putLong(fileId);
            nack.putInt(seqNo);
            nack.flip();
            
            sendDataChannelMessage(nack);
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error sending NACK: %s%n", e.getMessage());
        }
    }
    
    /**
     * Send FIN signal
     */
    private void sendFin(long fileId) {
        try {
            ByteBuffer fin = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
            fin.put(FIN);
            fin.putLong(fileId);
            fin.flip();
            
            sendDataChannelMessage(fin);
            System.out.printf("[DCFileTransfer] 🏁 FIN sent for fileId=%d%n", fileId);
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error sending FIN: %s%n", e.getMessage());
        }
    }
    
    /**
     * Complete file reception
     */
    private void completeFileReception(ReceiveState state) {
        try {
            state.close();
            
            long transferTime = System.currentTimeMillis() - state.transferStartTime;
            double throughput = (state.fileSize * 8.0 / 1_000_000) / (transferTime / 1000.0);
            
            System.out.printf("[DCFileTransfer] ✅ File transfer complete: %s (%d bytes, %.2f Mbps)%n",
                state.filePath.getFileName(), state.fileSize, throughput);
            
            receivingFiles.remove(state.fileId);
            
            if (transferCallback != null) {
                transferCallback.onFileReceived(username, state.fileId, state.filePath, state.fileSize);
            }
            
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error completing file reception: %s%n", e.getMessage());
        }
    }
    
    /**
     * Send message via DataChannel
     */
    private void sendDataChannelMessage(ByteBuffer data) {
        if (dataChannel.getState() != RTCDataChannelState.OPEN) {
            System.err.println("[DCFileTransfer] DataChannel not open!");
            return;
        }
        
        try {
            RTCDataChannelBuffer buffer = new RTCDataChannelBuffer(data, true);
            dataChannel.send(buffer);
        } catch (Exception e) {
            System.err.printf("[DCFileTransfer] Error sending DataChannel message: %s%n", e.getMessage());
        }
    }
    
    /**
     * Send state
     */
    private static class SendState {
        enum State { IDLE, WAITING_ACK, SENDING, WAITING_NACK, COMPLETE, FAILED }
        
        final long fileId;
        final String receiver;
        final Path filePath;
        final long fileSize;
        final int totalChunks;
        final BitSet sentChunks;
        
        State state;
        CompletableFuture<Boolean> completionFuture;
        
        SendState(long fileId, String receiver, Path filePath, long fileSize, int totalChunks) {
            this.fileId = fileId;
            this.receiver = receiver;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.totalChunks = totalChunks;
            this.sentChunks = new BitSet(totalChunks);
            this.state = State.IDLE;
            this.completionFuture = new CompletableFuture<>();
        }
    }
    
    /**
     * Receive state
     */
    private static class ReceiveState {
        enum State { IDLE, RECEIVING, COMPLETE }
        
        final long fileId;
        final Path filePath;
        final long fileSize;
        final int totalChunks;
        final BitSet receivedChunks;
        
        ChunkManager chunkManager;  // For large files (unlimited size support)
        FileChannel fileChannel;
        
        State state;
        long transferStartTime;
        
        ReceiveState(long fileId, Path filePath, long fileSize, int totalChunks) {
            this.fileId = fileId;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.totalChunks = totalChunks;
            this.receivedChunks = new BitSet(totalChunks);
            this.state = State.IDLE;
        }
        
        void initializeChunkManager() throws IOException {
            fileChannel = FileChannel.open(filePath, 
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.SYNC);
            
            // Truncate file to exact size
            fileChannel.truncate(fileSize);
            
            // Initialize ChunkManager for large file support (unlimited size)
            // ChunkManager constructor: (FileChannel fc, long fileSize, int sliceSize)
            chunkManager = new ChunkManager(fileChannel, fileSize, SLICE_SIZE);
            
            System.out.printf("[DCFileTransfer] 📦 ChunkManager initialized for fileId=%d (%d MB)%n",
                fileId, fileSize / (1024 * 1024));
        }
        
        void writeChunk(int seqNo, byte[] data) throws IOException {
            long position = (long) seqNo * SLICE_SIZE;
            
            // Use ChunkManager for large files (LRU caching with automatic chunk mapping)
            if (chunkManager != null && fileSize > 256L << 20) {  // > 256 MB
                // Binary search to find which chunk contains this sequence
                int chunkIndex = chunkManager.findChunkForSequence(seqNo);
                
                // Get mapped buffer for this chunk (cached with LRU eviction)
                MappedByteBuffer chunkBuffer = chunkManager.getChunk(chunkIndex);
                
                // Get chunk metadata to calculate offset
                ChunkMetadata meta = chunkManager.getChunkMetadata(chunkIndex);
                
                // Calculate offset within the chunk
                // globalSeqStart is the first sequence in this chunk
                int offsetInChunk = (int) ((seqNo - meta.globalSeqStart) * SLICE_SIZE);
                
                // Write to mapped buffer
                chunkBuffer.position(offsetInChunk);
                chunkBuffer.put(data);
                
                if (seqNo % 100 == 0) {
                    System.out.printf("[DCFileTransfer] 📦 Chunk %d: wrote seq %d at offset %d%n",
                        chunkIndex, seqNo, offsetInChunk);
                }
            } else {
                // Small files (< 256 MB): direct FileChannel write (simpler, no chunking overhead)
                ByteBuffer buffer = ByteBuffer.wrap(data);
                fileChannel.position(position);
                while (buffer.hasRemaining()) {
                    fileChannel.write(buffer);
                }
            }
            
            receivedChunks.set(seqNo);
        }
        
        int[] getMissingChunks() {
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                if (!receivedChunks.get(i)) {
                    missing.add(i);
                }
            }
            return missing.stream().mapToInt(Integer::intValue).toArray();
        }
        
        void close() throws IOException {
            if (fileChannel != null) {
                fileChannel.force(true);  // Sync to disk
                fileChannel.close();
            }
        }
    }
    
    public void shutdown() {
        scheduler.shutdownNow();
        sendingFiles.clear();
        for (ReceiveState state : receivingFiles.values()) {
            try {
                state.close();
            } catch (IOException e) {
                System.err.println("Error closing receiver: " + e.getMessage());
            }
        }
        receivingFiles.clear();
    }
}
