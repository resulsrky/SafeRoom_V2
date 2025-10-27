package com.saferoom.gui.service;


import com.saferoom.gui.model.Message;
import com.saferoom.gui.model.User;
import com.saferoom.client.ClientMenu;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;

/**
 * Mesajları yöneten, gönderen ve alan servis.
 * Singleton deseni ile tasarlandı, yani uygulamanın her yerinden tek bir
 * nesnesine erişilebilir.
 */
public class ChatService {

    // Singleton deseni için statik nesne
    private static final ChatService instance = new ChatService();

    // Current user's username (set by ClientMenu during initialization)
    private String currentUsername = null;

    // Veri saklama alanı (eskiden kontrolcüdeydi)
    private final Map<String, ObservableList<Message>> channelMessages = new HashMap<>();

    // DİKKAT: Bu, yeni bir mesaj geldiğinde bunu dinleyenleri haberdar eden sihirli kısımdır.
    private final ObjectProperty<Message> newMessageProperty = new SimpleObjectProperty<>();

    // Constructor'ı private yaparak dışarıdan yeni nesne oluşturulmasını engelliyoruz.
    private ChatService() {
        // Başlangıç için sahte verileri yükle
        setupDummyMessages();
    }

    // Servisin tek nesnesine erişim metodu
    public static ChatService getInstance() {
        return instance;
    }

    /**
     * Set the current user's username (called by ClientMenu during initialization)
     * @param username The current user's username
     */
    public void setCurrentUsername(String username) {
        this.currentUsername = username;
        System.out.printf("[ChatService] 👤 Current user set to: %s%n", username);
    }

    /**
     * Get the current user's username
     * @return The current user's username
     */
    public String getCurrentUsername() {
        return currentUsername;
    }

    /**
     * Belirtilen kanala yeni bir mesaj gönderir.
     * P2P bağlantı varsa P2P kullanır, yoksa server relay kullanır.
     * @param channelId Sohbet kanalının ID'si
     * @param text Gönderilecek mesaj metni
     * @param sender Mesajı gönderen kullanıcı
     */
    public void sendMessage(String channelId, String text, User sender) {
        if (text == null || text.trim().isEmpty()) return;

        Message newMessage = new Message(
                text,
                sender.getId(),
                sender.getName().isEmpty() ? "" : sender.getName().substring(0, 1)
        );

        // Mesajı ilgili kanalın listesine ekle
        ObservableList<Message> messages = getMessagesForChannel(channelId);
        messages.add(newMessage);

        // Try WebRTC DataChannel P2P messaging first
        boolean sentViaP2P = false;
        
        // Check if we have active WebRTC DataChannel connection
        com.saferoom.p2p.P2PConnectionManager p2pManager = 
            com.saferoom.p2p.P2PConnectionManager.getInstance();
        
        if (p2pManager.hasActiveConnection(channelId)) {
            try {
                System.out.printf("[Chat] 📡 Sending via WebRTC DataChannel to %s%n", channelId);
                
                java.util.concurrent.CompletableFuture<Boolean> future = 
                    p2pManager.sendMessage(channelId, text);
                
                // Wait for send completion (with timeout)
                sentViaP2P = future.get(2, java.util.concurrent.TimeUnit.SECONDS);
                
                if (sentViaP2P) {
                    System.out.printf("[Chat] ✅ Message sent via WebRTC DataChannel to %s%n", channelId);
                } else {
                    System.out.printf("[Chat] ⚠️ WebRTC DataChannel send failed to %s%n", channelId);
                }
            } catch (Exception e) {
                System.err.printf("[Chat] ❌ WebRTC DataChannel error: %s%n", e.getMessage());
                sentViaP2P = false;
            }
        }
        
        if (!sentViaP2P) {
            System.out.printf("[Chat] 📡 No P2P connection with %s - would use server relay%n", channelId);
            // TODO: Implement server relay messaging
        }

        // Update contact's last message (from me)
        try {
            com.saferoom.gui.service.ContactService.getInstance()
                .updateLastMessage(channelId, text, true);
        } catch (Exception e) {
            System.err.println("[Chat] Error updating contact last message: " + e.getMessage());
        }

        // Yeni mesaj geldiğini tüm dinleyenlere haber ver!
        newMessageProperty.set(newMessage);
    }

    /**
     * Belirtilen kanalın mesaj listesini döndürür.
     * @param channelId Sohbet kanalının ID'si
     * @return O kanala ait ObservableList<Message>
     */
    public ObservableList<Message> getMessagesForChannel(String channelId) {
        return channelMessages.computeIfAbsent(channelId, k -> FXCollections.observableArrayList());
    }

    // Yeni mesaj dinleyicisi için property'e erişim metodu
    public ObjectProperty<Message> newMessageProperty() {
        return newMessageProperty;
    }
    
    /**
     * P2P'den gelen mesajı al ve GUI'de göster
     */
    public void receiveP2PMessage(String sender, String receiver, String messageText) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf("[Chat] 📥 P2P message received: %s -> %s: \"%s\"%n", sender, receiver, messageText);
        System.out.printf("[Chat] 🔍 Stack trace:%n");
        for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
            if (elem.getClassName().contains("saferoom")) {
                System.out.printf("    at %s.%s(%s:%d)%n", 
                    elem.getClassName(), elem.getMethodName(), 
                    elem.getFileName(), elem.getLineNumber());
            }
        }
        System.out.println("═══════════════════════════════════════════════════════════════");
        
        Message incomingMessage = new Message(
            messageText,
            sender,
            sender.isEmpty() ? "?" : sender.substring(0, 1).toUpperCase()
        );
        
        // Mesajı doğru channel'a ekle
        ObservableList<Message> messages = getMessagesForChannel(sender);
        messages.add(incomingMessage);
        
        System.out.printf("[Chat] 📬 Updated contact last message for %s%n", sender);
        System.out.printf("[Chat] ✅ P2P message added to channel: %s%n", sender);
        
        // Update contact's last message (not from me - will increment unread if not active)
        try {
            com.saferoom.gui.service.ContactService contactService = 
                com.saferoom.gui.service.ContactService.getInstance();
            
            // Add contact if doesn't exist
            if (!contactService.hasContact(sender)) {
                contactService.addNewContact(sender);
            }
            
            // Update last message (isFromMe = false)
            contactService.updateLastMessage(sender, messageText, false);
            
            System.out.printf("[Chat] 📬 Updated contact last message for %s%n", sender);
            
        } catch (Exception e) {
            System.err.println("[Chat] Error updating contact for P2P message: " + e.getMessage());
        }
        
        // GUI'yi güncelle
        newMessageProperty.set(incomingMessage);
        
        System.out.printf("[Chat] ✅ P2P message added to channel: %s%n", sender);
    }
    
    /**
     * Dosya transfer işlemi başlat (P2P)
     * @param targetUser Dosya gönderilecek kullanıcı
     * @param filePath Gönderilecek dosyanın yolu
     */
    public void sendFile(String targetUser, java.nio.file.Path filePath) {
        if (targetUser == null || filePath == null) {
            System.err.println("[Chat] ❌ Invalid sendFile parameters");
            return;
        }
        
        System.out.printf("[Chat] 📁 Starting file transfer: %s -> %s%n", 
            filePath.getFileName(), targetUser);
        
        // Check P2P connection (WebRTC DataChannel)
        if (!ClientMenu.isP2PMessagingAvailable(targetUser)) {
            System.err.printf("[Chat] ❌ No P2P connection with %s%n", targetUser);
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("P2P Error");
                alert.setHeaderText("No P2P Connection");
                alert.setContentText("Cannot send file - no active P2P connection with " + targetUser);
                alert.showAndWait();
            });
            return;
        }
        
        try {
            // Use WebRTC DataChannel file transfer (P2PConnectionManager)
            System.out.printf("[Chat] 📤 Sending file via DataChannel to %s: %s%n",
                targetUser, filePath.getFileName());
            
            com.saferoom.p2p.P2PConnectionManager.getInstance()
                .sendFile(targetUser, filePath)
                .thenAccept(success -> {
                    if (success) {
                        System.out.printf("[Chat] ✅ File transfer completed: %s%n", filePath.getFileName());
                        
                        javafx.application.Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.INFORMATION);
                            alert.setTitle("File Transfer");
                            alert.setHeaderText("File Sent Successfully");
                            alert.setContentText("File " + filePath.getFileName() + " sent to " + targetUser);
                            alert.show();
                        });
                    } else {
                        System.err.printf("[Chat] ❌ File transfer failed: %s%n", filePath.getFileName());
                        
                        javafx.application.Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.ERROR);
                            alert.setTitle("File Transfer Error");
                            alert.setHeaderText("Failed to Send File");
                            alert.setContentText("Could not send " + filePath.getFileName());
                            alert.showAndWait();
                        });
                    }
                });
            
        } catch (Exception e) {
            System.err.printf("[Chat] ❌ File transfer error: %s%n", e.getMessage());
            e.printStackTrace();
            
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("File Transfer Error");
                alert.setHeaderText("Failed to Send File");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    // No dummy messages - start with clean slate
    private void setupDummyMessages() {
        // All chat channels start empty - real messages will be added via P2P
        System.out.println("[ChatService] 🧹 Started with clean message history - no dummy messages");
    }
}