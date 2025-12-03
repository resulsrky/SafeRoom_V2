package com.saferoom.filevault.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM Encryption Service
 * 
 * Simple, production-grade file encryption
 */
public class EncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Generate 256-bit AES key
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_SIZE, secureRandom);
        return keyGen.generateKey();
    }
    
    /**
     * Generate 12-byte IV
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        return iv;
    }
    
    /**
     * Encrypt file
     */
    public static EncryptionResult encrypt(Path inputFile, Path outputFile, SecretKey key) throws Exception {
        long startTime = System.currentTimeMillis();
        byte[] iv = generateIV();
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        
        byte[] inputBytes = Files.readAllBytes(inputFile);
        byte[] encryptedBytes = cipher.doFinal(inputBytes);
        
        try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            fos.write(iv);
            fos.write(encryptedBytes);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        return new EncryptionResult(
            true,
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(key.getEncoded()),
            inputBytes.length,
            iv.length + encryptedBytes.length,
            duration
        );
    }
    
    /**
     * Decrypt file
     */
    public static DecryptionResult decrypt(Path encryptedFile, Path outputFile, SecretKey key) throws Exception {
        long startTime = System.currentTimeMillis();
        
        byte[] encryptedData = Files.readAllBytes(encryptedFile);
        
        if (encryptedData.length < IV_SIZE) {
            return new DecryptionResult(false, "Invalid encrypted file", 0, 0);
        }
        
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(encryptedData, 0, iv, 0, IV_SIZE);
        
        byte[] ciphertext = new byte[encryptedData.length - IV_SIZE];
        System.arraycopy(encryptedData, IV_SIZE, ciphertext, 0, ciphertext.length);
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            Files.write(outputFile, decryptedBytes);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return new DecryptionResult(true, "Success", encryptedData.length, decryptedBytes.length, duration);
            
        } catch (Exception e) {
            return new DecryptionResult(false, "Wrong key or corrupted file", encryptedData.length, 0);
        }
    }
    
    /**
     * Convert Base64 to SecretKey
     */
    public static SecretKey base64ToKey(String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    // Result classes
    public static class EncryptionResult {
        public final boolean success;
        public final String ivBase64;
        public final String keyBase64;
        public final long originalSize;
        public final long encryptedSize;
        public final long durationMs;
        
        public EncryptionResult(boolean success, String ivBase64, String keyBase64,
                                long originalSize, long encryptedSize, long durationMs) {
            this.success = success;
            this.ivBase64 = ivBase64;
            this.keyBase64 = keyBase64;
            this.originalSize = originalSize;
            this.encryptedSize = encryptedSize;
            this.durationMs = durationMs;
        }
    }
    
    public static class DecryptionResult {
        public final boolean success;
        public final String message;
        public final long encryptedSize;
        public final long decryptedSize;
        public final long durationMs;
        
        public DecryptionResult(boolean success, String message, long encryptedSize, long decryptedSize) {
            this(success, message, encryptedSize, decryptedSize, 0);
        }
        
        public DecryptionResult(boolean success, String message, long encryptedSize, long decryptedSize, long durationMs) {
            this.success = success;
            this.message = message;
            this.encryptedSize = encryptedSize;
            this.decryptedSize = decryptedSize;
            this.durationMs = durationMs;
        }
    }
}

