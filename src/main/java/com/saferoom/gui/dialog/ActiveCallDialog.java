package com.saferoom.gui.dialog;

import com.saferoom.webrtc.CallManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.time.Duration;
import java.time.Instant;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

/**
 * Dialog for active WebRTC call
 * Shows video preview, controls (mute, camera toggle, end call), and call duration
 */
public class ActiveCallDialog {
    
    private final Stage stage;
    private final String remoteUsername;
    private final String callId;
    private final boolean videoEnabled;
    private final CallManager callManager;
    
    // UI Components
    private Label durationLabel;
    private Button muteButton;
    private Button cameraButton;
    private Button endCallButton;
    private Pane videoPreviewPane;
    private Pane remoteVideoPane;
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private Instant callStartTime;
    private Timeline durationTimer;
    
    /**
     * Create active call dialog
     * 
     * @param remoteUsername Remote user's username
     * @param callId Unique call identifier
     * @param videoEnabled Whether video is enabled
     * @param callManager CallManager instance
     */
    public ActiveCallDialog(String remoteUsername, String callId, boolean videoEnabled, CallManager callManager) {
        this.remoteUsername = remoteUsername;
        this.callId = callId;
        this.videoEnabled = videoEnabled;
        this.callManager = callManager;
        this.callStartTime = Instant.now();
        this.stage = createDialog();
    }
    
    private Stage createDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.DECORATED);
        dialog.setTitle("Active Call - " + remoteUsername);
        dialog.setResizable(true);
        dialog.setMinWidth(640);
        dialog.setMinHeight(480);
        
        // Main container
        BorderPane mainContainer = new BorderPane();
        mainContainer.getStyleClass().add("dialog-container");
        
        // ========== TOP BAR ==========
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");
        
        Label userLabel = new Label(remoteUsername);
        userLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        durationLabel = new Label("00:00");
        durationLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        
        topBar.getChildren().addAll(userLabel, spacer, durationLabel);
        mainContainer.setTop(topBar);
        
        // ========== CENTER (VIDEO AREA) ==========
        StackPane videoArea = new StackPane();
        videoArea.setStyle("-fx-background-color: #1a1a1a;");
        
        // Remote video pane (full screen)
        remoteVideoPane = new Pane();
        remoteVideoPane.setStyle("-fx-background-color: #2c3e50;");
        
        // Placeholder for remote video
        VBox remoteVideoPlaceholder = new VBox(10);
        remoteVideoPlaceholder.setAlignment(Pos.CENTER);
        FontIcon remoteIcon = new FontIcon(videoEnabled ? 
            FontAwesomeSolid.VIDEO : FontAwesomeSolid.USER);
        remoteIcon.setIconSize(80);
        remoteIcon.setIconColor(Color.web("#95a5a6"));
        Label remoteLabel = new Label(videoEnabled ? 
            "Remote Video" : remoteUsername);
        remoteLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 16px;");
        remoteVideoPlaceholder.getChildren().addAll(remoteIcon, remoteLabel);
        remoteVideoPane.getChildren().add(remoteVideoPlaceholder);
        
        // Bind placeholder to pane center
        remoteVideoPlaceholder.layoutXProperty().bind(
            remoteVideoPane.widthProperty().subtract(remoteVideoPlaceholder.widthProperty()).divide(2)
        );
        remoteVideoPlaceholder.layoutYProperty().bind(
            remoteVideoPane.heightProperty().subtract(remoteVideoPlaceholder.heightProperty()).divide(2)
        );
        
        // Local video preview (small, bottom-right corner)
        videoPreviewPane = new Pane();
        videoPreviewPane.setStyle("-fx-background-color: #34495e; -fx-background-radius: 8px;");
        videoPreviewPane.setPrefSize(160, 120);
        videoPreviewPane.setMaxSize(160, 120);
        
        VBox previewPlaceholder = new VBox(5);
        previewPlaceholder.setAlignment(Pos.CENTER);
        FontIcon previewIcon = new FontIcon(FontAwesomeSolid.USER);
        previewIcon.setIconSize(32);
        previewIcon.setIconColor(Color.web("#7f8c8d"));
        Label previewLabel = new Label("You");
        previewLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        previewPlaceholder.getChildren().addAll(previewIcon, previewLabel);
        videoPreviewPane.getChildren().add(previewPlaceholder);
        
        // Center preview placeholder
        previewPlaceholder.layoutXProperty().bind(
            videoPreviewPane.widthProperty().subtract(previewPlaceholder.widthProperty()).divide(2)
        );
        previewPlaceholder.layoutYProperty().bind(
            videoPreviewPane.heightProperty().subtract(previewPlaceholder.heightProperty()).divide(2)
        );
        
        // Position video preview in bottom-right corner
        StackPane.setAlignment(videoPreviewPane, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(videoPreviewPane, new Insets(15));
        
        if (videoEnabled) {
            videoArea.getChildren().addAll(remoteVideoPane, videoPreviewPane);
        } else {
            videoArea.getChildren().add(remoteVideoPane);
        }
        
        mainContainer.setCenter(videoArea);
        
        // ========== BOTTOM BAR (CONTROLS) ==========
        HBox controlBar = new HBox(20);
        controlBar.setPadding(new Insets(15));
        controlBar.setAlignment(Pos.CENTER);
        controlBar.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8);");
        
        // Mute/Unmute button
        muteButton = createControlButton(
            FontAwesomeSolid.MICROPHONE,
            "#3498db",
            "Mute"
        );
        muteButton.setOnAction(e -> toggleMute());
        
        // Camera toggle button (only for video calls)
        cameraButton = createControlButton(
            FontAwesomeSolid.VIDEO,
            "#9b59b6",
            "Turn Off Camera"
        );
        cameraButton.setOnAction(e -> toggleCamera());
        cameraButton.setVisible(videoEnabled);
        cameraButton.setManaged(videoEnabled);
        
        // End call button
        endCallButton = createControlButton(
            FontAwesomeSolid.PHONE_SLASH,
            "#e74c3c",
            "End Call"
        );
        endCallButton.setOnAction(e -> endCall());
        
        controlBar.getChildren().addAll(muteButton, cameraButton, endCallButton);
        mainContainer.setBottom(controlBar);
        
        // Create scene
        Scene scene = new Scene(mainContainer, 800, 600);
        try {
            scene.getStylesheets().add(
                getClass().getResource("/styles/styles.css").toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("[ActiveCallDialog] Warning: Could not load styles.css");
        }
        
        dialog.setScene(scene);
        
        // Handle window close
        dialog.setOnCloseRequest(e -> {
            e.consume(); // Prevent default close
            endCall();
        });
        
        return dialog;
    }
    
    /**
     * Create a control button with icon
     */
    private Button createControlButton(FontAwesomeSolid icon, String color, String tooltip) {
        Button button = new Button();
        FontIcon buttonIcon = new FontIcon(icon);
        buttonIcon.setIconSize(24);
        button.setGraphic(buttonIcon);
        button.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-pref-width: 60px; -fx-pref-height: 60px; " +
            "-fx-background-radius: 30px;",
            color
        ));
        
        // Hover effect
        String hoverColor = adjustBrightness(color, 0.8);
        button.setOnMouseEntered(e -> 
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; " +
                "-fx-pref-width: 60px; -fx-pref-height: 60px; " +
                "-fx-background-radius: 30px;",
                hoverColor
            ))
        );
        button.setOnMouseExited(e -> 
            button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; " +
                "-fx-pref-width: 60px; -fx-pref-height: 60px; " +
                "-fx-background-radius: 30px;",
                color
            ))
        );
        
        return button;
    }
    
    /**
     * Adjust color brightness (simple darkening)
     */
    private String adjustBrightness(String hexColor, double factor) {
        // Simple darkening by reducing RGB values
        try {
            Color color = Color.web(hexColor);
            return String.format("#%02x%02x%02x",
                (int)(color.getRed() * 255 * factor),
                (int)(color.getGreen() * 255 * factor),
                (int)(color.getBlue() * 255 * factor)
            );
        } catch (Exception e) {
            return hexColor;
        }
    }
    
    /**
     * Toggle microphone mute
     */
    private void toggleMute() {
        isMuted = !isMuted;
        
        if (callManager != null) {
            callManager.toggleAudio(!isMuted); // Pass the new state (true = enabled, false = disabled)
        }
        
        FontIcon icon = new FontIcon(isMuted ? 
            FontAwesomeSolid.MICROPHONE_SLASH : FontAwesomeSolid.MICROPHONE);
        icon.setIconSize(24);
        muteButton.setGraphic(icon);
        
        String color = isMuted ? "#e74c3c" : "#3498db";
        muteButton.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-pref-width: 60px; -fx-pref-height: 60px; " +
            "-fx-background-radius: 30px;",
            color
        ));
        
        System.out.printf("[ActiveCallDialog] Microphone %s%n", isMuted ? "muted" : "unmuted");
    }
    
    /**
     * Toggle camera on/off
     */
    private void toggleCamera() {
        isCameraOn = !isCameraOn;
        
        if (callManager != null) {
            callManager.toggleVideo(isCameraOn); // Pass the new state
        }
        
        FontIcon icon = new FontIcon(isCameraOn ? 
            FontAwesomeSolid.VIDEO : FontAwesomeSolid.VIDEO_SLASH);
        icon.setIconSize(24);
        cameraButton.setGraphic(icon);
        
        String color = isCameraOn ? "#9b59b6" : "#e74c3c";
        cameraButton.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-pref-width: 60px; -fx-pref-height: 60px; " +
            "-fx-background-radius: 30px;",
            color
        ));
        
        // Update local preview visibility
        videoPreviewPane.setVisible(isCameraOn);
        
        System.out.printf("[ActiveCallDialog] Camera %s%n", isCameraOn ? "enabled" : "disabled");
    }
    
    /**
     * End the call
     */
    private void endCall() {
        System.out.printf("[ActiveCallDialog] Ending call: %s%n", callId);
        
        stopDurationTimer();
        
        if (callManager != null) {
            callManager.endCall();
        }
        
        close();
    }
    
    /**
     * Start duration timer
     */
    private void startDurationTimer() {
        durationTimer = new Timeline(
            new KeyFrame(javafx.util.Duration.seconds(1), e -> updateDuration())
        );
        durationTimer.setCycleCount(Timeline.INDEFINITE);
        durationTimer.play();
    }
    
    /**
     * Stop duration timer
     */
    private void stopDurationTimer() {
        if (durationTimer != null) {
            durationTimer.stop();
        }
    }
    
    /**
     * Update call duration label
     */
    private void updateDuration() {
        Duration duration = Duration.between(callStartTime, Instant.now());
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        String durationText;
        if (hours > 0) {
            durationText = String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            durationText = String.format("%02d:%02d", minutes, seconds % 60);
        }
        
        durationLabel.setText(durationText);
    }
    
    /**
     * Show the dialog
     */
    public void show() {
        stage.show();
        startDurationTimer();
    }
    
    /**
     * Close the dialog
     */
    public void close() {
        stopDurationTimer();
        if (stage.isShowing()) {
            stage.close();
        }
    }
    
    /**
     * Get the call ID
     */
    public String getCallId() {
        return callId;
    }
    
    /**
     * Get the remote username
     */
    public String getRemoteUsername() {
        return remoteUsername;
    }
    
    /**
     * Check if microphone is muted
     */
    public boolean isMuted() {
        return isMuted;
    }
    
    /**
     * Check if camera is on
     */
    public boolean isCameraOn() {
        return isCameraOn;
    }
}
