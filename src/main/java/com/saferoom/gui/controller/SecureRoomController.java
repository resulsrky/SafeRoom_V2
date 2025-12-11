package com.saferoom.gui.controller;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXToggleButton;
import com.saferoom.gui.MainApp;
import com.saferoom.gui.components.VideoPanel;
import com.saferoom.gui.model.Meeting;
import com.saferoom.gui.model.UserRole;
import com.saferoom.gui.utils.WindowStateManager;
import com.saferoom.webrtc.WebRTCClient;
import dev.onvoid.webrtc.media.video.*;
import dev.onvoid.webrtc.media.MediaDevices;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class SecureRoomController {

    @FXML private JFXButton backButton;
    @FXML private Button initiateButton;
    @FXML private JFXComboBox<String> audioInputBox;
    @FXML private JFXComboBox<String> audioOutputBox;
    @FXML private JFXComboBox<String> cameraSourceBox;
    @FXML private JFXToggleButton cameraToggle;
    @FXML private JFXToggleButton micToggle;
    @FXML private ProgressBar micTestBar;
    @FXML private JFXSlider inputVolumeSlider;
    @FXML private JFXSlider outputVolumeSlider;
    @FXML private JFXCheckBox autoDestroyCheck;
    @FXML private JFXCheckBox noLogsCheck;
    @FXML private TextField roomIdField;
    @FXML private StackPane cameraView; // Sol taraftaki kamera preview alanı

    // Window state manager for dragging functionality
    private WindowStateManager windowStateManager = new WindowStateManager();
    private MainController mainController;

    private Timeline micAnimation;
    private Scene returnScene;
    
    // Camera preview
    private VideoPanel cameraPreview;
    private VideoDeviceSource videoSource;
    private VideoTrack videoTrack;
    
    // Selected devices
    private VideoDevice selectedCamera;
    private dev.onvoid.webrtc.media.audio.AudioDevice selectedMicrophone;
    private dev.onvoid.webrtc.media.audio.AudioDevice selectedSpeaker;

    /**
     * Ana controller referansını ayarlar (geri dönüş için)
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setReturnScene(Scene scene) {
        this.returnScene = scene;
    }

    @FXML
    public void initialize() {
        // Root pane'i bulup sürükleme işlevini etkinleştir
        // Bu view'da herhangi bir pane referansı olmadığı için, scene yüklendikten sonra eklenecek
        
        // Populate with real system devices
        populateAudioInputDevices();
        populateAudioOutputDevices();
        populateCameraDevices();
        
        // Add selection change listeners
        cameraSourceBox.setOnAction(e -> handleCameraSelection());
        audioInputBox.setOnAction(e -> handleMicrophoneSelection());
        audioOutputBox.setOnAction(e -> handleSpeakerSelection());

        backButton.setOnAction(event -> handleBack());
        initiateButton.setOnAction(event -> handleInitiate());

        startMicTestAnimation();
        
        // Initialize camera preview
        initializeCameraPreview();
        
        // Camera toggle listener
        cameraToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            toggleCameraPreview(newVal);
        });
        
        // Scene yüklendiğinde root pane'i bul ve sürükleme ekle
        backButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getRoot() instanceof javafx.scene.layout.Pane) {
                windowStateManager.setupWindowDrag((javafx.scene.layout.Pane) newScene.getRoot());
            }
        });
    }
    
    /**
     * Initialize camera preview in SecureRoom
     */
    private void initializeCameraPreview() {
        try {
            System.out.println("[SecureRoom] Initializing camera preview...");
            
            // Check if cameraView is injected
            if (cameraView == null) {
                System.err.println("[SecureRoom] cameraView is null - check FXML fx:id");
                return;
            }
            
            // Initialize WebRTC if not already done
            if (!WebRTCClient.isInitialized()) {
                WebRTCClient.initialize();
            }
            
            // Create VideoPanel with initial size
            cameraPreview = new VideoPanel(640, 480);
            
            // CRITICAL FIX: Wrap Canvas in custom Pane that controls Canvas size
            // Pane's layoutChildren prevents Canvas resize from affecting parent
            javafx.scene.layout.Pane canvasWrapper = new javafx.scene.layout.Pane() {
                @Override
                protected void layoutChildren() {
                    super.layoutChildren();
                    // Resize Canvas to match Pane size (Pane size is controlled by StackPane)
                    double w = getWidth();
                    double h = getHeight();
                    if (w > 0 && h > 0 && cameraPreview != null) {
                        cameraPreview.setWidth(w);
                        cameraPreview.setHeight(h);
                    }
                }
            };
            
            // Add Canvas to wrapper
            canvasWrapper.getChildren().add(cameraPreview);
            
            // Make wrapper fill StackPane (managed by parent)
            canvasWrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            
            // Add wrapper to cameraView
            cameraView.getChildren().clear();
            cameraView.getChildren().add(canvasWrapper);
            
            System.out.println("[SecureRoom] Camera preview wrapped in Pane (feedback-proof)");
            
            // Start camera if toggle is ON
            if (cameraToggle.isSelected()) {
                startCameraPreview();
            } else {
                // Show placeholder when camera is OFF
                showCameraPlaceholder();
            }
            
        } catch (Exception e) {
            System.err.println("[SecureRoom] Failed to initialize camera preview: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Show placeholder when camera is disabled
     */
    private void showCameraPlaceholder() {
        javafx.scene.layout.VBox placeholder = new javafx.scene.layout.VBox(10);
        placeholder.setAlignment(javafx.geometry.Pos.CENTER);
        
        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon("fas-video-slash");
        icon.setIconSize(64);
        icon.setStyle("-fx-icon-color: #6b7280;");
        
        javafx.scene.control.Label label = new javafx.scene.control.Label("Camera is disabled");
        label.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 14px;");
        
        placeholder.getChildren().addAll(icon, label);
        
        // Add on top of VideoPanel
        if (!cameraView.getChildren().contains(placeholder)) {
            cameraView.getChildren().add(placeholder);
        }
    }
    
    /**
     * Hide placeholder
     */
    private void hideCameraPlaceholder() {
        // Remove any VBox (placeholder) from cameraView, keep only VideoPanel
        cameraView.getChildren().removeIf(node -> node instanceof javafx.scene.layout.VBox);
    }
    
    /**
     * Start camera preview
     */
    private void startCameraPreview() {
        try {
            System.out.println("[SecureRoom] Starting camera preview...");
            
            var factory = WebRTCClient.getFactory();
            if (factory == null) {
                System.err.println("[SecureRoom] Factory not available");
                return;
            }
            
            // Use selected camera (or first available if none selected)
            VideoDevice camera = selectedCamera;
            if (camera == null) {
                List<VideoDevice> cameras = MediaDevices.getVideoCaptureDevices();
                if (cameras.isEmpty()) {
                    System.err.println("[SecureRoom] No cameras found!");
                    return;
                }
                camera = cameras.get(0);
                selectedCamera = camera;
            }
            System.out.println("[SecureRoom] Using camera: " + camera.getName());
            
            // Create video source
            videoSource = new VideoDeviceSource();
            videoSource.setVideoCaptureDevice(camera);
            
            // Set capability
            VideoCaptureCapability capability = new VideoCaptureCapability(640, 480, 30);
            videoSource.setVideoCaptureCapability(capability);
            
            // Create video track
            videoTrack = factory.createVideoTrack("preview_video", videoSource);
            videoTrack.setEnabled(true); // CRITICAL: Enable track
            
            // Start capturing
            videoSource.start();
            
            // Attach to VideoPanel
            if (cameraPreview != null) {
                cameraPreview.attachVideoTrack(videoTrack);
            }
            
            // Hide placeholder
            hideCameraPlaceholder();
            
            System.out.println("[SecureRoom] ✅ Camera preview started");
            
        } catch (Exception e) {
            System.err.println("[SecureRoom] Failed to start camera: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Stop camera preview
     */
    private void stopCameraPreview() {
        try {
            System.out.println("[SecureRoom] Stopping camera preview...");
            
            // Detach from VideoPanel
            if (cameraPreview != null) {
                cameraPreview.detachVideoTrack();
            }
            
            // Stop and dispose video source
            if (videoSource != null) {
                videoSource.stop();
                videoSource.dispose();
                videoSource = null;
            }
            
            // Dispose video track
            if (videoTrack != null) {
                videoTrack.setEnabled(false);
                videoTrack.dispose();
                videoTrack = null;
            }
            
            // Show placeholder
            showCameraPlaceholder();
            
            System.out.println("[SecureRoom] Camera preview stopped");
            
        } catch (Exception e) {
            System.err.println("[SecureRoom] Error stopping camera: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Toggle camera preview on/off
     */
    private void toggleCameraPreview(boolean enabled) {
        if (enabled) {
            startCameraPreview();
        } else {
            stopCameraPreview();
        }
    }

    private void handleInitiate() {
        if (micAnimation != null) micAnimation.stop();
        
        // CRITICAL: Stop camera preview before leaving (prevent camera conflict)
        stopCameraPreview();

        // Ana controller üzerinden meeting panel'i yükle (content değiştirme)
        if (mainController != null) {
            try {
                FXMLLoader meetingLoader = new FXMLLoader(MainApp.class.getResource("/view/MeetingPanelView.fxml"));
                Parent meetingRoot = meetingLoader.load();

                MeetingPanelController meetingController = meetingLoader.getController();
                meetingController.setMainController(mainController);

                // =========================================================================
                // DEGISIKLIK BURADA: Oda ID'si bos ise rastgele bir ID olusturuyoruz.
                // =========================================================================
                String roomId = roomIdField.getText();
                if (roomId == null || roomId.trim().isEmpty()) {
                    // Basit bir rastgele ID oluşturma (daha karmaşık bir yapı da kullanılabilir)
                    roomId = "SecureRoom-" + (new Random().nextInt(90000) + 10000);
                }
                // =========================================================================

                // Meeting oluştur
                Meeting secureMeeting = new Meeting(roomId, "Secure Room");
                
                // Kamera ve mikrofon ayarlarını al
                boolean withCamera = cameraToggle.isSelected();
                boolean withMic = micToggle.isSelected();

                // Meeting controller'ı camera/mic durumlarıyla başlat
                meetingController.initData(secureMeeting, UserRole.ADMIN, withCamera, withMic);

                // Ana controller'ın content area'sına meeting panel'i yükle
                mainController.contentArea.getChildren().setAll(meetingRoot);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startMicTestAnimation() {
        micAnimation = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            if (micToggle.isSelected()) {
                micTestBar.setProgress(new Random().nextDouble() * 0.7);
            } else {
                micTestBar.setProgress(0);
            }
        }));
        micAnimation.setCycleCount(Animation.INDEFINITE);
        micAnimation.play();
    }

    private void handleBack() {
        if (micAnimation != null) micAnimation.stop();
        
        // CRITICAL: Stop camera preview before leaving
        stopCameraPreview();

        // Ana controller üzerinden ana görünüme geri dön
        if (mainController != null) {
            mainController.returnToMainView();
        }
    }
    
    /**
     * Populate camera combo box with real system cameras
     */
    private void populateCameraDevices() {
        try {
            List<VideoDevice> cameras = MediaDevices.getVideoCaptureDevices();
            cameraSourceBox.getItems().clear();
            
            if (cameras.isEmpty()) {
                cameraSourceBox.getItems().add("No cameras found");
                cameraSourceBox.setDisable(true);
                System.out.println("[SecureRoom] No cameras available");
                return;
            }
            
            for (VideoDevice camera : cameras) {
                cameraSourceBox.getItems().add(camera.getName());
            }
            
            cameraSourceBox.getSelectionModel().selectFirst();
            selectedCamera = cameras.get(0);
            
            System.out.printf("[SecureRoom] Found %d camera(s)%n", cameras.size());
        } catch (Exception e) {
            System.err.println("[SecureRoom] Error enumerating cameras: " + e.getMessage());
            cameraSourceBox.getItems().add("Error loading cameras");
            cameraSourceBox.setDisable(true);
        }
    }
    
    /**
     * Populate microphone combo box with real system audio input devices
     */
    private void populateAudioInputDevices() {
        // Run on background thread to avoid COM threading issues on Windows
        new Thread(() -> {
            try {
                List<dev.onvoid.webrtc.media.audio.AudioDevice> microphones = 
                    MediaDevices.getAudioCaptureDevices();
                
                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    audioInputBox.getItems().clear();
                    
                    if (microphones.isEmpty()) {
                        audioInputBox.getItems().add("No microphones found");
                        audioInputBox.setDisable(true);
                        System.out.println("[SecureRoom] No microphones available");
                        return;
                    }
                    
                    for (dev.onvoid.webrtc.media.audio.AudioDevice mic : microphones) {
                        audioInputBox.getItems().add(mic.getName());
                    }
                    
                    audioInputBox.getSelectionModel().selectFirst();
                    if (!microphones.isEmpty()) {
                        selectedMicrophone = microphones.get(0);
                    }
                    
                    System.out.printf("[SecureRoom] Found %d microphone(s)%n", microphones.size());
                });
            } catch (Exception e) {
                System.err.println("[SecureRoom] Error enumerating microphones: " + e.getMessage());
                javafx.application.Platform.runLater(() -> {
                    audioInputBox.getItems().clear();
                    audioInputBox.getItems().add("Error loading microphones");
                    audioInputBox.setDisable(true);
                });
            }
        }, "AudioInputEnumeration").start();
    }
    
    /**
     * Populate speaker combo box with real system audio output devices
     */
    private void populateAudioOutputDevices() {
        // Run on background thread to avoid COM threading issues on Windows
        new Thread(() -> {
            try {
                List<dev.onvoid.webrtc.media.audio.AudioDevice> speakers = 
                    MediaDevices.getAudioRenderDevices();
                
                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    audioOutputBox.getItems().clear();
                    
                    if (speakers.isEmpty()) {
                        audioOutputBox.getItems().add("No speakers found");
                        audioOutputBox.setDisable(true);
                        System.out.println("[SecureRoom] No speakers available");
                        return;
                    }
                    
                    for (dev.onvoid.webrtc.media.audio.AudioDevice speaker : speakers) {
                        audioOutputBox.getItems().add(speaker.getName());
                    }
                    
                    audioOutputBox.getSelectionModel().selectFirst();
                    if (!speakers.isEmpty()) {
                        selectedSpeaker = speakers.get(0);
                    }
                    
                    System.out.printf("[SecureRoom] Found %d speaker(s)%n", speakers.size());
                });
            } catch (Exception e) {
                System.err.println("[SecureRoom] Error enumerating speakers: " + e.getMessage());
                javafx.application.Platform.runLater(() -> {
                    audioOutputBox.getItems().clear();
                    audioOutputBox.getItems().add("Error loading speakers");
                    audioOutputBox.setDisable(true);
                });
            }
        }, "AudioOutputEnumeration").start();
    }
    
    /**
     * Handle camera selection change
     */
    private void handleCameraSelection() {
        int selectedIndex = cameraSourceBox.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        
        try {
            List<VideoDevice> cameras = MediaDevices.getVideoCaptureDevices();
            if (selectedIndex < cameras.size()) {
                selectedCamera = cameras.get(selectedIndex);
                System.out.println("[SecureRoom] Camera selected: " + selectedCamera.getName());
                
                // Restart preview with new camera if currently active
                if (cameraToggle.isSelected() && videoSource != null) {
                    stopCameraPreview();
                    startCameraPreview();
                }
            }
        } catch (Exception e) {
            System.err.println("[SecureRoom] Error changing camera: " + e.getMessage());
        }
    }
    
    /**
     * Handle microphone selection change
     */
    private void handleMicrophoneSelection() {
        int selectedIndex = audioInputBox.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        
        try {
            List<dev.onvoid.webrtc.media.audio.AudioDevice> microphones = 
                MediaDevices.getAudioCaptureDevices();
            if (selectedIndex < microphones.size()) {
                selectedMicrophone = microphones.get(selectedIndex);
                System.out.println("[SecureRoom] Microphone selected: " + selectedMicrophone.getName());
                // Microphone will be applied when joining the meeting
            }
        } catch (Exception e) {
            System.err.println("[SecureRoom] Error changing microphone: " + e.getMessage());
        }
    }
    
    /**
     * Handle speaker selection change
     */
    private void handleSpeakerSelection() {
        int selectedIndex = audioOutputBox.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;
        
        try {
            List<dev.onvoid.webrtc.media.audio.AudioDevice> speakers = 
                MediaDevices.getAudioRenderDevices();
            if (selectedIndex < speakers.size()) {
                selectedSpeaker = speakers.get(selectedIndex);
                System.out.println("[SecureRoom] Speaker selected: " + selectedSpeaker.getName());
                // Speaker will be applied when joining the meeting
            }
        } catch (Exception e) {
            System.err.println("[SecureRoom] Error changing speaker: " + e.getMessage());
        }
    }
    
    /**
     * Get selected camera device
     */
    public VideoDevice getSelectedCamera() {
        return selectedCamera;
    }
    
    /**
     * Get selected microphone device
     */
    public dev.onvoid.webrtc.media.audio.AudioDevice getSelectedMicrophone() {
        return selectedMicrophone;
    }
    
    /**
     * Get selected speaker device
     */
    public dev.onvoid.webrtc.media.audio.AudioDevice getSelectedSpeaker() {
        return selectedSpeaker;
    }
}