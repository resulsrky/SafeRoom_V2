package com.saferoom.gui.model;

import java.nio.file.Path;

/**
 * FileVault'ta gösterilen dosya modeli
 * DM, Meeting veya Local kaynaklı olabilir
 */
public class VaultFile {
    private final String id;
    private final String name;
    private final String size;
    private final String date;
    private final String type;      // "Documents", "Images", "Archives"
    private final String source;    // "dm", "meeting", "local"
    private final Path path;
    private final long sizeBytes;
    private boolean encrypted;

    // Eski constructor (geriye uyumluluk)
    public VaultFile(String name, String size, String date, String type) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.size = size;
        this.date = date;
        this.type = type;
        this.source = "local";
        this.path = null;
        this.sizeBytes = 0;
        this.encrypted = false;
    }

    // Yeni tam constructor
    public VaultFile(String id, String name, long sizeBytes, long timestamp, 
                     String type, String source, Path path, boolean encrypted) {
        this.id = id;
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.size = formatSize(sizeBytes);
        this.date = formatDate(timestamp);
        this.type = type;
        this.source = source;
        this.path = path;
        this.encrypted = encrypted;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSize() { return size; }
    public String getDate() { return date; }
    public String getType() { return type; }
    public String getSource() { return source; }
    public Path getPath() { return path; }
    public long getSizeBytes() { return sizeBytes; }
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatDate(long timestamp) {
        if (timestamp == 0) return "";
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    /**
     * Dosya uzantısından kategori belirle
     */
    public static String categoryFromExtension(String fileName) {
        if (fileName == null) return "Documents";
        String lower = fileName.toLowerCase();
        
        // Images
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || 
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") ||
            lower.endsWith(".tiff") || lower.endsWith(".svg") || lower.endsWith(".ico")) {
            return "Images";
        }
        
        // Archives
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
            lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".bz2") ||
            lower.endsWith(".xz") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return "Archives";
        }
        
        // Documents (default for text-based files)
        return "Documents";
    }
}
