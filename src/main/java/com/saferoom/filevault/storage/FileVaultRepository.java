package com.saferoom.filevault.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.saferoom.filevault.model.FileMetadata;
import com.saferoom.filevault.model.FileMetadata.FileSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FileVault Repository
 * 
 * Manages SQLite database for unified file vault
 * Handles DM files, Meeting files, and Local encrypted files
 */
public class FileVaultRepository {
    
    private static FileVaultRepository instance;
    private Connection connection;
    private final Gson gson = new GsonBuilder().create();
    
    private static final String DB_NAME = "file_vault.db";
    
    private FileVaultRepository(String dataDir) throws SQLException {
        try {
            Path dirPath = Paths.get(dataDir);
            Files.createDirectories(dirPath);
            
            String dbPath = Paths.get(dataDir, DB_NAME).toString();
            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            
            initializeSchema();
            
            System.out.println("[FileVault] ✅ Repository initialized at: " + dbPath);
            
        } catch (Exception e) {
            System.err.println("[FileVault] ❌ Failed to initialize: " + e.getMessage());
            throw new SQLException("Repository initialization failed", e);
        }
    }
    
    public static synchronized FileVaultRepository initialize(String dataDir) throws SQLException {
        if (instance == null) {
            instance = new FileVaultRepository(dataDir);
        }
        return instance;
    }
    
    public static FileVaultRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FileVaultRepository not initialized");
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return instance != null;
    }
    
    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Main files table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vault_files (
                    file_id TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    source_user TEXT,
                    original_name TEXT NOT NULL,
                    stored_path TEXT NOT NULL UNIQUE,
                    file_size INTEGER NOT NULL,
                    mime_type TEXT,
                    is_encrypted BOOLEAN NOT NULL DEFAULT 0,
                    encryption_key TEXT,
                    iv_base64 TEXT,
                    is_compressed BOOLEAN NOT NULL DEFAULT 0,
                    compression_type TEXT,
                    category_tags TEXT,
                    is_starred BOOLEAN NOT NULL DEFAULT 0,
                    thumbnail BLOB,
                    hash_sha256 TEXT,
                    created_at INTEGER NOT NULL,
                    modified_at INTEGER NOT NULL,
                    metadata_json TEXT
                )
                """);
            
            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vault_files_source ON vault_files(source)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vault_files_starred ON vault_files(is_starred)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vault_files_created ON vault_files(created_at DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vault_files_category ON vault_files(category_tags)");
            
            // User categories table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_categories (
                    category_id TEXT PRIMARY KEY,
                    category_name TEXT NOT NULL UNIQUE,
                    icon TEXT,
                    color TEXT,
                    created_at INTEGER NOT NULL
                )
                """);
            
            // Vault containers table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vault_containers (
                    container_id TEXT PRIMARY KEY,
                    container_name TEXT NOT NULL,
                    encryption_key TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    file_count INTEGER DEFAULT 0
                )
                """);
            
            // File to container mapping
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS file_to_container (
                    container_id TEXT NOT NULL,
                    file_id TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    PRIMARY KEY (container_id, file_id),
                    FOREIGN KEY (container_id) REFERENCES vault_containers(container_id) ON DELETE CASCADE,
                    FOREIGN KEY (file_id) REFERENCES vault_files(file_id) ON DELETE CASCADE
                )
                """);
            
            // Operations log
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vault_operations_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    operation_type TEXT NOT NULL,
                    file_id TEXT,
                    file_name TEXT,
                    success BOOLEAN NOT NULL,
                    error_message TEXT,
                    timestamp INTEGER NOT NULL
                )
                """);
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vault_log_timestamp ON vault_operations_log(timestamp DESC)");
            
            // Insert default categories (if not exists)
            stmt.execute("""
                INSERT OR IGNORE INTO user_categories (category_id, category_name, icon, color, created_at) 
                VALUES 
                    ('cat_documents', 'Documents', 'fas-file-alt', '#3b82f6', """ + System.currentTimeMillis() + ")");
            
            stmt.execute("""
                INSERT OR IGNORE INTO user_categories (category_id, category_name, icon, color, created_at) 
                VALUES 
                    ('cat_images', 'Images', 'fas-image', '#10b981', """ + System.currentTimeMillis() + ")");
            
            stmt.execute("""
                INSERT OR IGNORE INTO user_categories (category_id, category_name, icon, color, created_at) 
                VALUES 
                    ('cat_archives', 'Archives', 'fas-file-archive', '#f59e0b', """ + System.currentTimeMillis() + ")");
            
            System.out.println("[FileVault] Database schema initialized");
            
        } catch (Exception e) {
            System.err.println("[FileVault] Schema initialization error: " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Schema initialization failed", e);
        }
    }
    
    /**
     * Insert a new file
     */
    public boolean insertFile(FileMetadata file) throws SQLException {
        String sql = """
            INSERT INTO vault_files (
                file_id, source, source_user, original_name, stored_path, file_size, mime_type,
                is_encrypted, encryption_key, iv_base64, is_compressed, compression_type,
                category_tags, is_starred, hash_sha256, created_at, modified_at, metadata_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, file.getFileId());
            pstmt.setString(2, file.getSource().name().toLowerCase());
            pstmt.setString(3, file.getSourceUser());
            pstmt.setString(4, file.getOriginalName());
            pstmt.setString(5, file.getStoredPath());
            pstmt.setLong(6, file.getFileSize());
            pstmt.setString(7, file.getMimeType());
            pstmt.setBoolean(8, file.isEncrypted());
            pstmt.setString(9, file.getEncryptionKey());
            pstmt.setString(10, file.getIvBase64());
            pstmt.setBoolean(11, file.isCompressed());
            pstmt.setString(12, file.getCompressionType());
            pstmt.setString(13, String.join(",", file.getCategoryTags()));
            pstmt.setBoolean(14, file.isStarred());
            pstmt.setString(15, file.getHashSha256());
            pstmt.setLong(16, file.getCreatedAt());
            pstmt.setLong(17, file.getModifiedAt());
            pstmt.setString(18, gson.toJson(file.getMetadata()));
            
            int affected = pstmt.executeUpdate();
            
            System.out.printf("[FileVault] ✅ Indexed file: %s (source: %s)%n", 
                file.getOriginalName(), file.getSource());
            
            return affected > 0;
        }
    }
    
    /**
     * Get all files
     */
    public List<FileMetadata> getAllFiles() throws SQLException {
        String sql = "SELECT * FROM vault_files ORDER BY created_at DESC";
        return queryFiles(sql);
    }
    
    /**
     * Get files by source
     */
    public List<FileMetadata> getFilesBySource(FileSource source) throws SQLException {
        String sql = "SELECT * FROM vault_files WHERE source = ? ORDER BY created_at DESC";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, source.name().toLowerCase());
            return queryFiles(pstmt);
        }
    }
    
    /**
     * Get files by category
     */
    public List<FileMetadata> getFilesByCategory(String category) throws SQLException {
        if ("All".equals(category)) {
            return getAllFiles();
        } else if ("Starred".equals(category)) {
            return getStarredFiles();
        }
        
        String sql = "SELECT * FROM vault_files WHERE category_tags LIKE ? ORDER BY created_at DESC";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "%" + category + "%");
            return queryFiles(pstmt);
        }
    }
    
    /**
     * Get starred files
     */
    public List<FileMetadata> getStarredFiles() throws SQLException {
        String sql = "SELECT * FROM vault_files WHERE is_starred = 1 ORDER BY created_at DESC";
        return queryFiles(sql);
    }
    
    /**
     * Search files
     */
    public List<FileMetadata> searchFiles(String query) throws SQLException {
        String sql = """
            SELECT * FROM vault_files 
            WHERE original_name LIKE ? OR category_tags LIKE ? 
            ORDER BY created_at DESC
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
            return queryFiles(pstmt);
        }
    }
    
    /**
     * Update file starred status
     */
    public boolean updateStarred(String fileId, boolean starred) throws SQLException {
        String sql = "UPDATE vault_files SET is_starred = ?, modified_at = ? WHERE file_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setBoolean(1, starred);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, fileId);
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Update file categories
     */
    public boolean updateCategories(String fileId, Set<String> categories) throws SQLException {
        String sql = "UPDATE vault_files SET category_tags = ?, modified_at = ? WHERE file_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, String.join(",", categories));
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, fileId);
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Delete file
     */
    public boolean deleteFile(String fileId) throws SQLException {
        String sql = "DELETE FROM vault_files WHERE file_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Get user categories
     */
    public List<UserCategory> getUserCategories() throws SQLException {
        String sql = "SELECT * FROM user_categories ORDER BY category_name";
        List<UserCategory> categories = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                UserCategory cat = new UserCategory();
                cat.categoryId = rs.getString("category_id");
                cat.categoryName = rs.getString("category_name");
                cat.icon = rs.getString("icon");
                cat.color = rs.getString("color");
                cat.createdAt = rs.getLong("created_at");
                categories.add(cat);
            }
        }
        
        return categories;
    }
    
    /**
     * Create user category
     */
    public boolean createUserCategory(String name, String icon, String color) throws SQLException {
        String categoryId = "cat_" + UUID.randomUUID().toString().substring(0, 8);
        String sql = "INSERT INTO user_categories (category_id, category_name, icon, color, created_at) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, categoryId);
            pstmt.setString(2, name);
            pstmt.setString(3, icon);
            pstmt.setString(4, color);
            pstmt.setLong(5, System.currentTimeMillis());
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Query files helper
     */
    private List<FileMetadata> queryFiles(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return mapResultSetToFiles(rs);
        }
    }
    
    private List<FileMetadata> queryFiles(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            return mapResultSetToFiles(rs);
        }
    }
    
    private List<FileMetadata> mapResultSetToFiles(ResultSet rs) throws SQLException {
        List<FileMetadata> files = new ArrayList<>();
        
        while (rs.next()) {
            FileMetadata file = new FileMetadata();
            file.setFileId(rs.getString("file_id"));
            file.setSource(FileSource.valueOf(rs.getString("source").toUpperCase()));
            file.setSourceUser(rs.getString("source_user"));
            file.setOriginalName(rs.getString("original_name"));
            file.setStoredPath(rs.getString("stored_path"));
            file.setFileSize(rs.getLong("file_size"));
            file.setMimeType(rs.getString("mime_type"));
            file.setEncrypted(rs.getBoolean("is_encrypted"));
            file.setEncryptionKey(rs.getString("encryption_key"));
            file.setIvBase64(rs.getString("iv_base64"));
            file.setCompressed(rs.getBoolean("is_compressed"));
            file.setCompressionType(rs.getString("compression_type"));
            
            String tags = rs.getString("category_tags");
            if (tags != null && !tags.isEmpty()) {
                file.setCategoryTags(new HashSet<>(Arrays.asList(tags.split(","))));
            }
            
            file.setStarred(rs.getBoolean("is_starred"));
            file.setHashSha256(rs.getString("hash_sha256"));
            file.setCreatedAt(rs.getLong("created_at"));
            file.setModifiedAt(rs.getLong("modified_at"));
            
            String metadataJson = rs.getString("metadata_json");
            if (metadataJson != null) {
                file.setMetadata(gson.fromJson(metadataJson, Map.class));
            }
            
            files.add(file);
        }
        
        return files;
    }
    
    /**
     * User Category data class
     */
    public static class UserCategory {
        public String categoryId;
        public String categoryName;
        public String icon;
        public String color;
        public long createdAt;
    }
}

