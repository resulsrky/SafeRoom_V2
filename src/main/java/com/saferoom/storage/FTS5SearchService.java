package com.saferoom.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Full-Text Search Service using SQLite FTS5
 * 
 * Features:
 * - Fast full-text search across all messages
 * - Ranked results (BM25 algorithm)
 * - Highlight support
 * - Conversation filtering
 * 
 * Usage:
 * - Search globally or within a conversation
 * - Get result positions for UI highlighting
 */
public class FTS5SearchService {
    
    private static final Logger LOGGER = Logger.getLogger(FTS5SearchService.class.getName());
    private final LocalDatabase database;
    
    public FTS5SearchService(LocalDatabase database) {
        this.database = database;
    }
    
    /**
     * Search result item
     */
    public static class SearchResult {
        private final String messageId;
        private final String conversationId;
        private final String content;
        private final long timestamp;
        private final double rank;
        
        public SearchResult(String messageId, String conversationId, String content, 
                          long timestamp, double rank) {
            this.messageId = messageId;
            this.conversationId = conversationId;
            this.content = content;
            this.timestamp = timestamp;
            this.rank = rank;
        }
        
        public String getMessageId() { return messageId; }
        public String getConversationId() { return conversationId; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
        public double getRank() { return rank; }
    }
    
    /**
     * Search messages globally
     * 
     * @param query Search query (supports FTS5 syntax: "word1 AND word2", "phrase search", etc.)
     * @param limit Maximum results
     * @return List of search results, ranked by relevance
     */
    public List<SearchResult> searchGlobal(String query, int limit) {
        String sql = """
            SELECT 
                m.id,
                m.conversation_id,
                m.content,
                m.timestamp,
                fts.rank
            FROM messages_fts fts
            JOIN messages m ON m.rowid = fts.rowid
            WHERE messages_fts MATCH ?
            ORDER BY fts.rank
            LIMIT ?
            """;
        
        List<SearchResult> results = new ArrayList<>();
        
        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SearchResult result = new SearchResult(
                        rs.getString("id"),
                        rs.getString("conversation_id"),
                        rs.getString("content"),
                        rs.getLong("timestamp"),
                        rs.getDouble("rank")
                    );
                    results.add(result);
                }
            }
            
            LOGGER.fine("Global search for '" + query + "' returned " + results.size() + " results");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Search failed for query: " + query, e);
        }
        
        return results;
    }
    
    /**
     * Search messages within a specific conversation
     * 
     * @param query Search query
     * @param conversationId Conversation to search in
     * @param limit Maximum results
     * @return List of search results
     */
    public List<SearchResult> searchInConversation(String query, String conversationId, int limit) {
        String sql = """
            SELECT 
                m.id,
                m.conversation_id,
                m.content,
                m.timestamp,
                fts.rank
            FROM messages_fts fts
            JOIN messages m ON m.rowid = fts.rowid
            WHERE messages_fts MATCH ? 
            AND m.conversation_id = ?
            ORDER BY fts.rank
            LIMIT ?
            """;
        
        List<SearchResult> results = new ArrayList<>();
        
        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setString(2, conversationId);
            stmt.setInt(3, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SearchResult result = new SearchResult(
                        rs.getString("id"),
                        rs.getString("conversation_id"),
                        rs.getString("content"),
                        rs.getLong("timestamp"),
                        rs.getDouble("rank")
                    );
                    results.add(result);
                }
            }
            
            LOGGER.fine("Conversation search returned " + results.size() + " results");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Conversation search failed", e);
        }
        
        return results;
    }
    
    /**
     * Get message IDs matching a query (for navigation)
     * Returns in chronological order for up/down navigation
     * 
     * @param query Search query
     * @param conversationId Conversation ID
     * @return List of message IDs in chronological order
     */
    public List<String> getMatchingMessageIds(String query, String conversationId) {
        String sql = """
            SELECT m.id
            FROM messages_fts fts
            JOIN messages m ON m.rowid = fts.rowid
            WHERE messages_fts MATCH ? 
            AND m.conversation_id = ?
            ORDER BY m.timestamp ASC
            """;
        
        List<String> messageIds = new ArrayList<>();
        
        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setString(2, conversationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messageIds.add(rs.getString("id"));
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get matching message IDs", e);
        }
        
        return messageIds;
    }
    
    /**
     * Get snippet with highlighted matches
     * Uses FTS5 snippet() function
     * 
     * @param query Search query
     * @param messageId Message ID
     * @return Snippet with <mark> tags around matches, or null if not found
     */
    public String getHighlightedSnippet(String query, String messageId) {
        String sql = """
            SELECT snippet(messages_fts, 1, '<mark>', '</mark>', '...', 64) as snippet
            FROM messages_fts fts
            JOIN messages m ON m.rowid = fts.rowid
            WHERE messages_fts MATCH ? 
            AND m.id = ?
            """;
        
        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setString(2, messageId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("snippet");
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get highlighted snippet", e);
        }
        
        return null;
    }
    
    /**
     * Count total matches for a query
     */
    public int countMatches(String query, String conversationId) {
        String sql;
        
        if (conversationId != null) {
            sql = """
                SELECT COUNT(*) as count
                FROM messages_fts fts
                JOIN messages m ON m.rowid = fts.rowid
                WHERE messages_fts MATCH ? 
                AND m.conversation_id = ?
                """;
        } else {
            sql = """
                SELECT COUNT(*) as count
                FROM messages_fts
                WHERE messages_fts MATCH ?
                """;
        }
        
        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, query);
            if (conversationId != null) {
                stmt.setString(2, conversationId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to count matches", e);
        }
        
        return 0;
    }
    
    /**
     * Optimize FTS index (run periodically for performance)
     */
    public void optimizeIndex() {
        try (PreparedStatement stmt = database.getConnection().prepareStatement(
                "INSERT INTO messages_fts(messages_fts) VALUES('optimize')")) {
            stmt.execute();
            LOGGER.info("FTS5 index optimized");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to optimize FTS index", e);
        }
    }
}

