package com.saferoom.filevault.service;

import com.saferoom.filevault.crypto.EncryptionService;
import com.saferoom.filevault.model.FileMetadata;
import com.saferoom.filevault.model.FileMetadata.FileSource;
import com.saferoom.filevault.storage.FileVaultRepository;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FileVault Service - Main Orchestrator
 * 
 * Manages all file operations:
 * - Indexing files from DM/Meeting
 * - Encrypting local files
 * - Categorization & starring
 * - Search & filter
 */
public class FileVaultService {
    
    private static FileVaultService instance;
    private final ExecutorService executorService;
    private final FileVaultRepository repository;
    private final FileIndexer indexer;
    private final Path vaultDirectory;
    
    private FileVaultService(String dataDir) throws Exception {
        this.executorService = Executors.newFixedThreadPool(4);
        this.repository = FileVaultRepository.initialize(dataDir);
        this.indexer = new FileIndexer(repository);
        
        // Create vault directory
        this.vaultDirectory = Paths.get(dataDir, "vault");
        Files.createDirectories(vaultDirectory);
        
        System.out.println("[FileVault] ✅ Service initialized");
        System.out.println("[FileVault] Vault: " + vaultDirectory);
    }
    
    public static synchronized FileVaultService initialize(String dataDir) throws Exception {
        if (instance == null) {
            instance = new FileVaultService(dataDir);
        }
        return instance;
    }
    
    public static FileVaultService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FileVaultService not initialized");
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * Index file from DM (called when file is received)
     */
    public CompletableFuture<FileMetadata> indexDMFileAsync(String senderUsername, Path filePath) {
        return CompletableFuture.supplyAsync(() -> 
            indexer.indexDMFile(senderUsername, filePath), executorService);
    }
    
    /**
     * Index file from Meeting (called when file is shared in meeting)
     */
    public CompletableFuture<FileMetadata> indexMeetingFileAsync(String senderUsername, Path filePath) {
        return CompletableFuture.supplyAsync(() -> 
            indexer.indexMeetingFile(senderUsername, filePath), executorService);
    }
    
    /**
     * Encrypt local file
     */
    public CompletableFuture<EncryptionResult> encryptLocalFileAsync(Path sourceFile, boolean compress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[FileVault] 🔒 Encrypting: " + sourceFile.getFileName());
                
                // Generate key
                SecretKey key = EncryptionService.generateKey();
                
                // TODO: Compress if requested
                Path fileToEncrypt = sourceFile;
                
                // Encrypt
                String encryptedFileName = sourceFile.getFileName().toString() + ".enc";
                Path encryptedFile = vaultDirectory.resolve(encryptedFileName);
                
                EncryptionService.EncryptionResult encResult = 
                    EncryptionService.encrypt(fileToEncrypt, encryptedFile, key);
                
                // Index
                FileMetadata metadata = indexer.indexLocalEncryptedFile(
                    sourceFile, encryptedFile, 
                    encResult.keyBase64, encResult.ivBase64,
                    compress, compress ? "zip" : null
                );
                
                System.out.println("[FileVault] ✅ Encryption complete");
                
                return new EncryptionResult(true, metadata, encResult.keyBase64, null);
                
            } catch (Exception e) {
                System.err.println("[FileVault] ❌ Encryption failed: " + e.getMessage());
                return new EncryptionResult(false, null, null, e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Decrypt file
     */
    public CompletableFuture<DecryptionResult> decryptFileAsync(String fileId, String keyBase64, Path outputDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get file metadata
                List<FileMetadata> files = repository.getAllFiles();
                FileMetadata file = files.stream()
                    .filter(f -> f.getFileId().equals(fileId))
                    .findFirst()
                    .orElse(null);
                
                if (file == null) {
                    return new DecryptionResult(false, null, "File not found");
                }
                
                // Decrypt
                SecretKey key = EncryptionService.base64ToKey(keyBase64);
                Path encryptedFile = Paths.get(file.getStoredPath());
                Path decryptedFile = outputDir.resolve(file.getOriginalName().replace(".enc", ""));
                
                EncryptionService.DecryptionResult decResult = 
                    EncryptionService.decrypt(encryptedFile, decryptedFile, key);
                
                if (decResult.success) {
                    return new DecryptionResult(true, decryptedFile.toString(), "Success");
                } else {
                    return new DecryptionResult(false, null, decResult.message);
                }
                
            } catch (Exception e) {
                return new DecryptionResult(false, null, e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Get all files
     */
    public CompletableFuture<List<FileMetadata>> getAllFilesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.getAllFiles();
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to load files: " + e.getMessage());
                return List.of();
            }
        }, executorService);
    }
    
    /**
     * Get files by category
     */
    public CompletableFuture<List<FileMetadata>> getFilesByCategoryAsync(String category) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.getFilesByCategory(category);
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to load category: " + e.getMessage());
                return List.of();
            }
        }, executorService);
    }
    
    /**
     * Search files
     */
    public CompletableFuture<List<FileMetadata>> searchFilesAsync(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.searchFiles(query);
            } catch (Exception e) {
                System.err.println("[FileVault] Search failed: " + e.getMessage());
                return List.of();
            }
        }, executorService);
    }
    
    /**
     * Toggle starred status
     */
    public CompletableFuture<Boolean> toggleStarredAsync(String fileId, boolean starred) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.updateStarred(fileId, starred);
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to update starred: " + e.getMessage());
                return false;
            }
        }, executorService);
    }
    
    /**
     * Update file categories
     */
    public CompletableFuture<Boolean> updateCategoriesAsync(String fileId, Set<String> categories) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.updateCategories(fileId, categories);
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to update categories: " + e.getMessage());
                return false;
            }
        }, executorService);
    }
    
    /**
     * Delete file
     */
    public CompletableFuture<Boolean> deleteFileAsync(String fileId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get file metadata
                List<FileMetadata> files = repository.getAllFiles();
                FileMetadata file = files.stream()
                    .filter(f -> f.getFileId().equals(fileId))
                    .findFirst()
                    .orElse(null);
                
                if (file != null) {
                    // Delete physical file
                    Path filePath = Paths.get(file.getStoredPath());
                    Files.deleteIfExists(filePath);
                    
                    // Delete from database
                    return repository.deleteFile(fileId);
                }
                return false;
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to delete file: " + e.getMessage());
                return false;
            }
        }, executorService);
    }
    
    /**
     * Get user categories
     */
    public CompletableFuture<List<FileVaultRepository.UserCategory>> getUserCategoriesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.getUserCategories();
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to load categories: " + e.getMessage());
                return List.of();
            }
        }, executorService);
    }
    
    /**
     * Create user category
     */
    public CompletableFuture<Boolean> createUserCategoryAsync(String name, String icon, String color) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.createUserCategory(name, icon, color);
            } catch (Exception e) {
                System.err.println("[FileVault] Failed to create category: " + e.getMessage());
                return false;
            }
        }, executorService);
    }
    
    /**
     * Shutdown service
     */
    public void shutdown() {
        executorService.shutdown();
        System.out.println("[FileVault] Service shutdown");
    }
    
    // Result classes
    public static class EncryptionResult {
        public final boolean success;
        public final FileMetadata metadata;
        public final String keyBase64;
        public final String errorMessage;
        
        public EncryptionResult(boolean success, FileMetadata metadata, String keyBase64, String errorMessage) {
            this.success = success;
            this.metadata = metadata;
            this.keyBase64 = keyBase64;
            this.errorMessage = errorMessage;
        }
    }
    
    public static class DecryptionResult {
        public final boolean success;
        public final String decryptedFilePath;
        public final String message;
        
        public DecryptionResult(boolean success, String decryptedFilePath, String message) {
            this.success = success;
            this.decryptedFilePath = decryptedFilePath;
            this.message = message;
        }
    }
}

