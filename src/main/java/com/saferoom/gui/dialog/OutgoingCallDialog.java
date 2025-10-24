package com.saferoom.gui.dialog;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.util.concurrent.CompletableFuture;

/**
 * Dialog for outgoing WebRTC calls
 * Shows "Calling..." status with Cancel button
 */
public class OutgoingCallDialog {
    
    private final Stage stage;
    private final CompletableFuture<Boolean> result; // true if call connected, false if cancelled
    private final String targetUsername;
    private final String callId;
    private final boolean videoEnabled;
    private final Label statusLabel;
    private Timeline pulseAnimation;
    
    /**
     * Create outgoing call dialog
     * 
     * @param targetUsername Username being called
     * @param callId Unique call identifier
     * @param videoEnabled Whether this is a video call
     */
    public OutgoingCallDialog(String targetUsername, String callId, boolean videoEnabled) {
        this.targetUsername = targetUsername;
        this.callId = callId;
        this.videoEnabled = videoEnabled;
        this.result = new CompletableFuture<>();
        this.statusLabel = new Label("Calling...");
        this.stage = createDialog();
        startPulseAnimation();
    }
    
    private Stage createDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Outgoing Call");
        dialog.setResizable(false);
        
        // Main container
        VBox mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(30));
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.getStyleClass().add("dialog-container");
        
        // Call icon
        FontIcon callIcon = new FontIcon(videoEnabled ? 
            FontAwesomeSolid.VIDEO : FontAwesomeSolid.PHONE);
        callIcon.setIconSize(64);
        callIcon.getStyleClass().add("call-icon-outgoing");
        callIcon.setStyle("-fx-fill: #3498db;");
        
        // Target user name
        Label userLabel = new Label(targetUsername);
        userLabel.getStyleClass().add("caller-name");
        userLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        
        // Status label (animated)
        statusLabel.getStyleClass().add("call-status-label");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #3498db;");
        
        // Cancel button
        Button cancelButton = new Button("Cancel");
        FontIcon cancelIcon = new FontIcon(FontAwesomeSolid.TIMES);
        cancelIcon.setIconSize(16);
        cancelButton.setGraphic(cancelIcon);
        cancelButton.getStyleClass().addAll("call-button", "cancel-button");
        cancelButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                             "-fx-pref-width: 120px; -fx-pref-height: 40px; -fx-background-radius: 20px;");
        cancelButton.setOnAction(e -> {
            result.complete(false);
            stopPulseAnimation();
            dialog.close();
        });
        
        // Hover effect
        cancelButton.setOnMouseEntered(e -> 
            cancelButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; " +
                                 "-fx-pref-width: 120px; -fx-pref-height: 40px; -fx-background-radius: 20px;"));
        cancelButton.setOnMouseExited(e -> 
            cancelButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                 "-fx-pref-width: 120px; -fx-pref-height: 40px; -fx-background-radius: 20px;"));
        
        // Add all elements
        mainContainer.getChildren().addAll(
            callIcon,
            userLabel,
            statusLabel,
            cancelButton
        );
        
        // Create scene
        Scene scene = new Scene(mainContainer, 400, 350);
        try {
            scene.getStylesheets().add(
                getClass().getResource("/styles/styles.css").toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("[OutgoingCallDialog] Warning: Could not load styles.css");
        }
        
        dialog.setScene(scene);
        
        // Handle window close
        dialog.setOnCloseRequest(e -> {
            if (!result.isDone()) {
                result.complete(false);
            }
            stopPulseAnimation();
        });
        
        return dialog;
    }
    
    /**
     * Start pulse animation for "Calling..." text
     */
    private void startPulseAnimation() {
        pulseAnimation = new Timeline(
            new KeyFrame(Duration.seconds(0), e -> statusLabel.setText("Calling")),
            new KeyFrame(Duration.seconds(0.5), e -> statusLabel.setText("Calling.")),
            new KeyFrame(Duration.seconds(1.0), e -> statusLabel.setText("Calling..")),
            new KeyFrame(Duration.seconds(1.5), e -> statusLabel.setText("Calling..."))
        );
        pulseAnimation.setCycleCount(Timeline.INDEFINITE);
        pulseAnimation.play();
    }
    
    /**
     * Stop pulse animation
     */
    private void stopPulseAnimation() {
        if (pulseAnimation != null) {
            pulseAnimation.stop();
        }
    }
    
    /**
     * Update status when call is accepted
     */
    public void callAccepted() {
        javafx.application.Platform.runLater(() -> {
            stopPulseAnimation();
            statusLabel.setText("Call accepted - Connecting...");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #27ae60;");
        });
    }
    
    /**
     * Update status when call is rejected
     */
    public void callRejected() {
        javafx.application.Platform.runLater(() -> {
            stopPulseAnimation();
            statusLabel.setText("Call rejected");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #e74c3c;");
            
            // Auto-close after 2 seconds
            Timeline closeTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> {
                    result.complete(false);
                    close();
                })
            );
            closeTimeline.play();
        });
    }
    
    /**
     * Update status when call is busy
     */
    public void callBusy() {
        javafx.application.Platform.runLater(() -> {
            stopPulseAnimation();
            statusLabel.setText("User is busy");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #f39c12;");
            
            // Auto-close after 2 seconds
            Timeline closeTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> {
                    result.complete(false);
                    close();
                })
            );
            closeTimeline.play();
        });
    }
    
    /**
     * Show the dialog
     */
    public void show() {
        stage.show();
    }
    
    /**
     * Show and wait for result
     * 
     * @return CompletableFuture<Boolean> - true if connected, false if cancelled
     */
    public CompletableFuture<Boolean> showAndWait() {
        stage.showAndWait();
        return result;
    }
    
    /**
     * Close the dialog
     */
    public void close() {
        stopPulseAnimation();
        if (stage.isShowing()) {
            stage.close();
        }
        if (!result.isDone()) {
            result.complete(false);
        }
    }
    
    /**
     * Notify that call connected successfully
     */
    public void notifyConnected() {
        if (!result.isDone()) {
            result.complete(true);
        }
        stopPulseAnimation();
        close();
    }
    
    /**
     * Get the call ID
     */
    public String getCallId() {
        return callId;
    }
    
    /**
     * Get the target username
     */
    public String getTargetUsername() {
        return targetUsername;
    }
}
