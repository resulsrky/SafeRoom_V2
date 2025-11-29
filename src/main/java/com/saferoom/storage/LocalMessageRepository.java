package com.saferoom.storage;

import com.saferoom.gui.model.Message;
import javafx.application.Platform;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository pattern for Message persistence
 * 
 * Acts as a bridge between ChatService (RAM) and LocalDatabase (Disk)
 * - Async operations to avoid blocking UI
 * - Thread-safe background writes
 * - Lazy loading support
 * 
 * Architecture:
 * RAM (ObservableList) ←→ Repository ←→ Disk (SQLite)
 */
public class LocalMessageRepository {
    
    private static final Logger LOGGER = Logger.getLogger(LocalMessageRepository.class.getName());
    private static LocalMessageRepository instance;
    
    private final LocalDatabase database;
    private final MessageDao messageDao;
    private final ExecutorService executor;
    
    private LocalMessageRepository(LocalDatabase database) {
        this.database = database;
        this.messageDao = new MessageDao(database);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MessagePersistence-Thread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Initialize repository
     */
    public static synchronized LocalMessageRepository initialize(LocalDatabase database) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new LocalMessageRepository(database);
        return instance;
    }
    
    /**
     * Get singleton instance
     */
    public static LocalMessageRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Repository not initialized");
        }
        return instance;
    }
    
    /**
     * Save message asynchronously (non-blocking)
     * 
     * @param message Message to save
     * @param conversationId Conversation ID
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> saveMessageAsync(Message message, String conversationId) {
        return CompletableFuture.runAsync(() -> {
            try {
                messageDao.insertMessage(message, conversationId);
                LOGGER.fine("Message saved to disk: " + message.getId());
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to save message: " + message.getId(), e);
                throw new RuntimeException("Failed to save message", e);
            }
        }, executor);
    }
    
    /**
     * Save message synchronously (blocking)
     * Use this only during bulk imports or when immediate persistence is required
     */
    public void saveMessageSync(Message message, String conversationId) throws SQLException {
        messageDao.insertMessage(message, conversationId);
    }
    
    /**
     * Load all messages for a conversation (synchronous)
     * This is called during startup to hydrate RAM from disk
     * 
     * @param conversationId Conversation ID
     * @return List of messages
     */
    public List<Message> loadMessages(String conversationId) {
        try {
            return messageDao.loadMessages(conversationId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load messages for: " + conversationId, e);
            return List.of();
        }
    }
    
    /**
     * Load messages with pagination
     * For lazy loading in UI
     */
    public CompletableFuture<List<Message>> loadMessagesPaginatedAsync(
            String conversationId, int limit, int offset) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return messageDao.loadMessagesPaginated(conversationId, limit, offset);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to load paginated messages", e);
                return List.of();
            }
        }, executor);
    }
    
    /**
     * Load media messages for Shared Media panel
     * 
     * @param conversationId Conversation ID
     * @return CompletableFuture<List<Message>>
     */
    public CompletableFuture<List<Message>> loadMediaMessagesAsync(String conversationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return messageDao.loadMediaMessages(conversationId);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to load media messages", e);
                return List.of();
            }
        }, executor);
    }
    
    /**
     * Delete a message
     */
    public CompletableFuture<Boolean> deleteMessageAsync(String messageId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return messageDao.deleteMessage(messageId);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to delete message: " + messageId, e);
                return false;
            }
        }, executor);
    }
    
    /**
     * Delete all messages in a conversation
     */
    public CompletableFuture<Integer> deleteConversationAsync(String conversationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return messageDao.deleteConversationMessages(conversationId);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to delete conversation: " + conversationId, e);
                return 0;
            }
        }, executor);
    }
    
    /**
     * Get message count for a conversation
     */
    public CompletableFuture<Integer> getMessageCountAsync(String conversationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return messageDao.getMessageCount(conversationId);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to get message count", e);
                return 0;
            }
        }, executor);
    }
    
    /**
     * Batch save messages (for bulk import)
     * Uses transaction for performance
     */
    public CompletableFuture<Void> saveMessagesBatchAsync(List<Message> messages, String conversationId) {
        return CompletableFuture.runAsync(() -> {
            try {
                database.beginTransaction();
                
                for (Message message : messages) {
                    messageDao.insertMessage(message, conversationId);
                }
                
                database.commit();
                LOGGER.info("Batch saved " + messages.size() + " messages");
                
            } catch (SQLException e) {
                try {
                    database.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed", rollbackEx);
                }
                LOGGER.log(Level.SEVERE, "Batch save failed", e);
                throw new RuntimeException("Batch save failed", e);
            }
        }, executor);
    }
    
    /**
     * Shutdown executor
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            LOGGER.info("Repository executor shut down");
        }
    }
}

