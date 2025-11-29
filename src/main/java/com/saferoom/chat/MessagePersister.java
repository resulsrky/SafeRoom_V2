package com.saferoom.chat;

import com.saferoom.gui.model.Message;
import com.saferoom.storage.LocalMessageRepository;
import com.saferoom.storage.MediaExtractorService;
import com.saferoom.storage.SqlCipherHelper;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Message Persister
 * 
 * Automatically persists messages to disk when they are sent or received
 * 
 * Integration Points:
 * - ChatService.sendMessage() → persist outgoing
 * - ChatService.receiveP2PMessage() → persist incoming
 * - File messages → extract thumbnail before persist
 * 
 * Architecture Flow:
 * Message created → Add to RAM → MessagePersister.persist() → SQLite
 */
public class MessagePersister {
    
    private static final Logger LOGGER = Logger.getLogger(MessagePersister.class.getName());
    private static MessagePersister instance;
    
    private final LocalMessageRepository repository;
    private final MediaExtractorService mediaExtractor;
    private boolean enabled = true;
    
    private MessagePersister(LocalMessageRepository repository) {
        this.repository = repository;
        this.mediaExtractor = MediaExtractorService.getInstance();
    }
    
    /**
     * Initialize persister
     */
    public static synchronized MessagePersister initialize(LocalMessageRepository repository) {
        if (instance != null) {
            LOGGER.warning("MessagePersister already initialized");
        }
        instance = new MessagePersister(repository);
        return instance;
    }
    
    /**
     * Get singleton instance
     */
    public static MessagePersister getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MessagePersister not initialized");
        }
        return instance;
    }
    
    /**
     * Persist a message asynchronously
     * 
     * @param message Message to persist
     * @param remoteUsername Remote user (conversation partner)
     * @param currentUsername Current user
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> persistMessageAsync(
            Message message,
            String remoteUsername,
            String currentUsername) {
        
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        String conversationId = SqlCipherHelper.generateConversationId(currentUsername, remoteUsername);
        
        // For file messages, extract thumbnail first
        if (message.getAttachment() != null && message.getAttachment().getLocalPath() != null) {
            return extractThumbnailAndPersist(message, conversationId);
        }
        
        // Regular text message - persist immediately
        return repository.saveMessageAsync(message, conversationId)
            .whenComplete((v, error) -> {
                if (error != null) {
                    LOGGER.log(Level.SEVERE, "Failed to persist message: " + message.getId(), error);
                } else {
                    LOGGER.fine("Message persisted: " + message.getId());
                }
            });
    }
    
    /**
     * Extract thumbnail for file message, then persist
     */
    private CompletableFuture<Void> extractThumbnailAndPersist(Message message, String conversationId) {
        Path filePath = message.getAttachment().getLocalPath();
        
        return mediaExtractor.extractThumbnailAuto(filePath)
            .thenCompose(thumbnail -> {
                // Set thumbnail if extracted
                if (thumbnail != null) {
                    message.setThumbnail(thumbnail);
                }
                
                // Persist message with thumbnail
                return repository.saveMessageAsync(message, conversationId);
            })
            .whenComplete((v, error) -> {
                if (error != null) {
                    LOGGER.log(Level.WARNING, "Failed to persist file message", error);
                } else {
                    LOGGER.fine("File message persisted with thumbnail: " + message.getId());
                }
            });
    }
    
    /**
     * Persist multiple messages in batch (for bulk operations)
     * Uses database transaction for performance
     * 
     * @param messages Messages to persist
     * @param remoteUsername Remote user
     * @param currentUsername Current user
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> persistMessagesBatchAsync(
            java.util.List<Message> messages,
            String remoteUsername,
            String currentUsername) {
        
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        String conversationId = SqlCipherHelper.generateConversationId(currentUsername, remoteUsername);
        
        return repository.saveMessagesBatchAsync(messages, conversationId)
            .whenComplete((v, error) -> {
                if (error != null) {
                    LOGGER.log(Level.SEVERE, "Batch persist failed", error);
                } else {
                    LOGGER.info("Batch persisted " + messages.size() + " messages");
                }
            });
    }
    
    /**
     * Delete a persisted message
     * 
     * @param messageId Message ID to delete
     * @return CompletableFuture<Boolean> True if deleted
     */
    public CompletableFuture<Boolean> deleteMessageAsync(String messageId) {
        return repository.deleteMessageAsync(messageId);
    }
    
    /**
     * Delete entire conversation history
     * 
     * @param remoteUsername Remote user
     * @param currentUsername Current user
     * @return CompletableFuture<Integer> Number of messages deleted
     */
    public CompletableFuture<Integer> deleteConversationAsync(
            String remoteUsername,
            String currentUsername) {
        
        String conversationId = SqlCipherHelper.generateConversationId(currentUsername, remoteUsername);
        return repository.deleteConversationAsync(conversationId);
    }
    
    /**
     * Enable or disable persistence
     * Useful for testing or temporary disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("Message persistence " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if persistence is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}

