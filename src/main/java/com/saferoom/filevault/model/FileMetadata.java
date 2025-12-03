package com.saferoom.filevault.model;

import javafx.scene.image.Image;
import java.util.*;

/**
 * File Metadata Model
 * 
 * Represents a file in the FileVault system
 * Can come from DM, Meeting, or Local encryption
 */
public class FileMetadata {
    
    private String fileId;
    private FileSource source;
    private String sourceUser;  // Who sent it (for DM/Meeting)
    private String originalName;
    private String storedPath;
    private long fileSize;
    private String mimeType;
    
    // Encryption
    private boolean encrypted;
    private String encryptionKey;  // Base64 (only for local files)
    private String ivBase64;
    
    // Compression
    private boolean compressed;
    private String compressionType;
    
    // Categorization
    private Set<String> categoryTags = new HashSet<>();
    private boolean starred;
    
    // UI
    private Image thumbnail;
    private String hashSha256;
    
    // Timestamps
    private long createdAt;
    private long modifiedAt;
    
    // Additional metadata
    private Map<String, Object> metadata = new HashMap<>();
    
    public enum FileSource {
        DM,
        MEETING,
        LOCAL
    }
    
    public enum FileCategory {
        ALL("All", "fas-th"),
        DOCUMENTS("Documents", "fas-file-alt"),
        IMAGES("Images", "fas-image"),
        ARCHIVES("Archives", "fas-file-archive"),
        STARRED("Starred", "fas-star");
        
        private final String displayName;
        private final String icon;
        
        FileCategory(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
        
        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
    }
    
    // Constructors
    public FileMetadata() {}
    
    public FileMetadata(String fileId, FileSource source, String originalName, String storedPath, long fileSize) {
        this.fileId = fileId;
        this.source = source;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }
    
    // Auto-categorize based on MIME type
    public FileCategory getAutoCategory() {
        if (mimeType == null) {
            return FileCategory.ALL;
        }
        
        if (mimeType.startsWith("image/")) {
            return FileCategory.IMAGES;
        } else if (mimeType.startsWith("application/pdf") || 
                   mimeType.startsWith("application/msword") ||
                   mimeType.startsWith("application/vnd.openxmlformats-officedocument") ||
                   mimeType.equals("text/plain")) {
            return FileCategory.DOCUMENTS;
        } else if (mimeType.equals("application/zip") ||
                   mimeType.equals("application/x-rar") ||
                   mimeType.equals("application/x-7z-compressed") ||
                   mimeType.equals("application/gzip")) {
            return FileCategory.ARCHIVES;
        }
        
        return FileCategory.ALL;
    }
    
    // Getters and Setters
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    
    public FileSource getSource() { return source; }
    public void setSource(FileSource source) { this.source = source; }
    
    public String getSourceUser() { return sourceUser; }
    public void setSourceUser(String sourceUser) { this.sourceUser = sourceUser; }
    
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
    
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    
    public String getIvBase64() { return ivBase64; }
    public void setIvBase64(String ivBase64) { this.ivBase64 = ivBase64; }
    
    public boolean isCompressed() { return compressed; }
    public void setCompressed(boolean compressed) { this.compressed = compressed; }
    
    public String getCompressionType() { return compressionType; }
    public void setCompressionType(String compressionType) { this.compressionType = compressionType; }
    
    public Set<String> getCategoryTags() { return categoryTags; }
    public void setCategoryTags(Set<String> categoryTags) { this.categoryTags = categoryTags; }
    
    public void addCategoryTag(String tag) { this.categoryTags.add(tag); }
    public void removeCategoryTag(String tag) { this.categoryTags.remove(tag); }
    
    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
    
    public Image getThumbnail() { return thumbnail; }
    public void setThumbnail(Image thumbnail) { this.thumbnail = thumbnail; }
    
    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}

