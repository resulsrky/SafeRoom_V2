package com.saferoom.gui.utils;

import com.saferoom.client.ClientMenu;
import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Heartbeat servisi - kullanıcının online durumunu takip eder
 */
public class HeartbeatService {
    
    private static HeartbeatService instance;
    private ScheduledExecutorService scheduler;
    private String sessionId;
    private boolean isRunning = false;
    
    private HeartbeatService() {
        // Constructor'da scheduler oluşturmuyoruz, lazy initialization yapacağız
        this.sessionId = UUID.randomUUID().toString();
    }
    
    public static synchronized HeartbeatService getInstance() {
        if (instance == null) {
            instance = new HeartbeatService();
        }
        return instance;
    }
    
    /**
     * Scheduler'ı yeniden oluştur (eğer terminate olmuşsa)
     */
    private void ensureSchedulerIsReady() {
        if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
            System.out.println("🔄 Creating new scheduler for HeartbeatService");
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "HeartbeatService-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
        }
    }
    
    /**
     * Heartbeat servisini başlat
     */
    public synchronized void startHeartbeat(String username) {
        if (isRunning) {
            System.out.println("💓 Heartbeat already running for: " + username);
            return;
        }
        
        System.out.println("💓 Starting heartbeat service for: " + username);
        
        // Scheduler'ın hazır olduğundan emin ol
        ensureSchedulerIsReady();
        
        isRunning = true;
        
        try {
            // Her 15 saniyede bir heartbeat gönder
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    com.saferoom.grpc.SafeRoomProto.HeartbeatResponse response = 
                        ClientMenu.sendHeartbeat(username, sessionId);
                    
                    if (!response.getSuccess()) {
                        System.err.println("❌ Heartbeat failed: " + response.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Heartbeat error: " + e.getMessage());
                }
            }, 0, 15, TimeUnit.SECONDS);
            
            System.out.println("✅ Heartbeat service started successfully");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to start heartbeat service: " + e.getMessage());
            isRunning = false;
            e.printStackTrace();
        }
    }
    
    /**
     * Heartbeat servisini durdur
     */
    public synchronized void stopHeartbeat() {
        stopHeartbeat(null);
    }
    
    /**
     * Heartbeat servisini durdur ve session'ı temizle
     */
    public synchronized void stopHeartbeat(String username) {
        if (!isRunning) {
            return;
        }
        
        System.out.println("💔 Stopping heartbeat service");
        
        // Session'ı sunucudan temizle
        if (username != null) {
            try {
                com.saferoom.client.ClientMenu.endUserSession(username, sessionId);
                System.out.println("🗑️ Session ended for: " + username);
            } catch (Exception e) {
                System.err.println("❌ Failed to end session: " + e.getMessage());
            }
        }
        
        isRunning = false;
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    System.out.println("⏰ Scheduler didn't terminate gracefully, forcing shutdown");
                    scheduler.shutdownNow();
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        System.err.println("❌ Scheduler did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("⚡ Interrupted while waiting for scheduler termination");
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Yeni session ID oluştur
        this.sessionId = UUID.randomUUID().toString();
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
}
