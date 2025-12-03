package com.saferoom.filevault.service;

import com.saferoom.gui.model.VaultFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SafeRoom/downloads klasörünü tarar ve dosyaları listeler
 * 
 * Tek gerçek kaynak: SafeRoom/downloads
 * DM, Meeting ve local dosyalar burada toplanır
 */
public class DownloadsScanner {
    
    private static DownloadsScanner instance;
    private final Path downloadsDir;
    private final Map<String, VaultFile> fileCache = new ConcurrentHashMap<>();
    
    private DownloadsScanner() {
        // SafeRoom proje dizinindeki downloads klasörü
        this.downloadsDir = Paths.get("downloads").toAbsolutePath();
        ensureDirectoryExists();
    }
    
    public static synchronized DownloadsScanner getInstance() {
        if (instance == null) {
            instance = new DownloadsScanner();
        }
        return instance;
    }
    
    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(downloadsDir);
            System.out.println("[FileVault] Downloads directory: " + downloadsDir);
        } catch (IOException e) {
            System.err.println("[FileVault] Failed to create downloads directory: " + e.getMessage());
        }
    }
    
    /**
     * Downloads klasörünü tara ve tüm dosyaları listele
     */
    public CompletableFuture<List<VaultFile>> scanAsync() {
        return CompletableFuture.supplyAsync(this::scan);
    }
    
    /**
     * Senkron tarama
     */
    public List<VaultFile> scan() {
        fileCache.clear();
        List<VaultFile> files = new ArrayList<>();
        
        if (!Files.exists(downloadsDir)) {
            System.out.println("[FileVault] Downloads directory does not exist");
            return files;
        }
        
        try {
            Files.walkFileTree(downloadsDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file)) {
                        VaultFile vf = createVaultFile(file, attrs);
                        if (vf != null) {
                            files.add(vf);
                            fileCache.put(vf.getId(), vf);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("[FileVault] Failed to visit: " + file);
                    return FileVisitResult.CONTINUE;
                }
            });
            
            // Tarihe göre sırala (en yeni önce)
            files.sort((a, b) -> b.getDate().compareTo(a.getDate()));
            
            System.out.printf("[FileVault] Scanned %d files from downloads%n", files.size());
            
        } catch (IOException e) {
            System.err.println("[FileVault] Scan error: " + e.getMessage());
        }
        
        return files;
    }
    
    /**
     * Kategoriye göre filtrele
     */
    public List<VaultFile> getByCategory(String category) {
        List<VaultFile> all = scan();
        
        if ("All".equalsIgnoreCase(category)) {
            return all;
        }
        
        return all.stream()
            .filter(f -> f.getType().equalsIgnoreCase(category))
            .collect(Collectors.toList());
    }
    
    /**
     * Path'ten VaultFile oluştur
     */
    private VaultFile createVaultFile(Path file, BasicFileAttributes attrs) {
        try {
            String fileName = file.getFileName().toString();
            long size = attrs.size();
            long modified = attrs.lastModifiedTime().toMillis();
            String category = VaultFile.categoryFromExtension(fileName);
            
            // Kaynak belirleme (dosya adından veya path'ten)
            String source = detectSource(file, fileName);
            
            // Şifreli mi kontrolü
            boolean encrypted = fileName.endsWith(".enc");
            
            // Orijinal isim (.enc varsa kaldır)
            String displayName = encrypted ? fileName.substring(0, fileName.length() - 4) : fileName;
            
            return new VaultFile(
                UUID.randomUUID().toString(),
                displayName,
                size,
                modified,
                category,
                source,
                file,
                encrypted
            );
            
        } catch (Exception e) {
            System.err.println("[FileVault] Error creating VaultFile: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Dosya kaynağını belirle (DM, Meeting, Local)
     */
    private String detectSource(Path file, String fileName) {
        // Path içinde ipucu ara
        String pathStr = file.toString().toLowerCase();
        
        if (pathStr.contains("meeting") || pathStr.contains("room_")) {
            return "meeting";
        }
        
        // Varsayılan olarak DM kabul et (SafeRoom'dan indirilen)
        // Local dosyalar kullanıcı tarafından sürükle-bırak ile eklenir
        return "dm";
    }
    
    /**
     * Yeni dosya ekle (dışarıdan sürükle-bırak)
     */
    public CompletableFuture<VaultFile> addLocalFile(Path sourceFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fileName = sourceFile.getFileName().toString();
                Path targetPath = downloadsDir.resolve(fileName);
                
                // Aynı isimde varsa yeniden adlandır
                int counter = 1;
                while (Files.exists(targetPath)) {
                    String base = fileName.substring(0, fileName.lastIndexOf('.'));
                    String ext = fileName.substring(fileName.lastIndexOf('.'));
                    targetPath = downloadsDir.resolve(base + "_" + counter + ext);
                    counter++;
                }
                
                // Kopyala
                Files.copy(sourceFile, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
                
                System.out.printf("[FileVault] Added local file: %s%n", targetPath.getFileName());
                
                // VaultFile oluştur
                BasicFileAttributes attrs = Files.readAttributes(targetPath, BasicFileAttributes.class);
                VaultFile vf = new VaultFile(
                    UUID.randomUUID().toString(),
                    targetPath.getFileName().toString(),
                    attrs.size(),
                    System.currentTimeMillis(),
                    VaultFile.categoryFromExtension(fileName),
                    "local",
                    targetPath,
                    false
                );
                
                fileCache.put(vf.getId(), vf);
                return vf;
                
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to add local file: " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * Dosya sil
     */
    public boolean deleteFile(VaultFile file) {
        try {
            if (file.getPath() != null && Files.exists(file.getPath())) {
                Files.delete(file.getPath());
                fileCache.remove(file.getId());
                System.out.printf("[FileVault] Deleted: %s%n", file.getName());
                return true;
            }
        } catch (Exception e) {
            System.err.println("[FileVault] Delete failed: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Downloads klasör yolunu al
     */
    public Path getDownloadsDir() {
        return downloadsDir;
    }
    
    /**
     * Dosya ara
     */
    public List<VaultFile> search(String query) {
        if (query == null || query.isBlank()) {
            return scan();
        }
        
        String lowerQuery = query.toLowerCase();
        return scan().stream()
            .filter(f -> f.getName().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());
    }
}

