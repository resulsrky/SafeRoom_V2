package com.saferoom.gui.dialog;

import dev.onvoid.webrtc.media.video.desktop.DesktopSource;
import com.saferoom.webrtc.WebRTCClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Improved Screen Share Picker Dialog with visual thumbnails
 * Similar to Google Meet's screen selection interface
 */
public class ImprovedScreenSharePickerDialog {
    
    @FXML private ToggleButton screensTab;
    @FXML private ToggleButton windowsTab;
    @FXML private VBox screensContainer;
    @FXML private VBox windowsContainer;
    @FXML private FlowPane screensGrid;
    @FXML private FlowPane windowsGrid;
    @FXML private Label statusLabel;
    @FXML private Button cancelButton;
    @FXML private Button shareButton;
    
    private Stage dialogStage;
    private DesktopSource selectedSource;
    private boolean isWindow = false;
    private boolean confirmed = false;
    private WebRTCClient webrtcClient; // For capturing thumbnails
    
    // Currently selected tile for visual feedback
    private SourceTile selectedTile = null;
    
    /**
     * Initialize the dialog
     */
    @FXML
    public void initialize() {
        System.out.println("[ImprovedScreenPicker] Initializing dialog...");
        
        // Setup tab toggle group
        ToggleGroup tabGroup = new ToggleGroup();
        screensTab.setToggleGroup(tabGroup);
        windowsTab.setToggleGroup(tabGroup);
        
        // Tab switching logic
        screensTab.setOnAction(e -> {
            if (screensTab.isSelected()) {
                showScreens();
            }
        });
        
        windowsTab.setOnAction(e -> {
            if (windowsTab.isSelected()) {
                showWindows();
            }
        });
        
        System.out.println("[ImprovedScreenPicker] Dialog initialized");
    }
    
    /**
     * Set available sources and populate grids with thumbnails
     */
    public void setAvailableSources(List<DesktopSource> screens, List<DesktopSource> windows) {
        setAvailableSources(screens, windows, null);
    }
    
    /**
     * Set available sources with WebRTC client for thumbnail capture
     */
    public void setAvailableSources(List<DesktopSource> screens, List<DesktopSource> windows, WebRTCClient client) {
        this.webrtcClient = client;
        
        System.out.printf("[ImprovedScreenPicker] Loading sources: %d screens, %d windows%n", 
            screens.size(), windows.size());
        
        // Clear existing items
        screensGrid.getChildren().clear();
        windowsGrid.getChildren().clear();
        
        // Create Robot for screen capture
        Robot robot = new Robot();
        
        // Create tiles for each screen with real thumbnails
        for (int i = 0; i < screens.size(); i++) {
            DesktopSource screen = screens.get(i);
            SourceTile tile = createSourceTile(screen, false, i + 1);
            screensGrid.getChildren().add(tile);
            
            // Capture screen thumbnail asynchronously
            final int screenIndex = i;
            CompletableFuture.runAsync(() -> {
                try {
                    // Get screen bounds
                    List<Screen> javafxScreens = Screen.getScreens();
                    if (screenIndex < javafxScreens.size()) {
                        Screen targetScreen = javafxScreens.get(screenIndex);
                        Rectangle2D bounds = targetScreen.getBounds();
                        
                        // Capture screenshot
                        WritableImage screenshot = robot.getScreenCapture(null, bounds);
                        
                        // Update tile on JavaFX thread
                        Platform.runLater(() -> {
                            tile.setThumbnail(screenshot);
                            System.out.printf("[ImprovedScreenPicker] ✅ Screen %d thumbnail captured%n", screenIndex + 1);
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[ImprovedScreenPicker] Error capturing screen thumbnail: " + e.getMessage());
                }
            });
        }
        
        // Create tiles for each window
        // Note: Window-specific capture is more complex, using screen capture as fallback
        for (DesktopSource window : windows) {
            SourceTile tile = createSourceTile(window, true, 0);
            windowsGrid.getChildren().add(tile);
            
            // For windows, capture the primary screen as a fallback
            // Real window-specific capture would require native APIs or AWT Robot with window positioning
            CompletableFuture.runAsync(() -> {
                try {
                    // Capture primary screen
                    Screen primaryScreen = Screen.getPrimary();
                    Rectangle2D bounds = primaryScreen.getBounds();
                    
                    // Scale down to smaller region (simulating window capture)
                    double x = bounds.getMinX() + bounds.getWidth() * 0.25;
                    double y = bounds.getMinY() + bounds.getHeight() * 0.25;
                    double w = bounds.getWidth() * 0.5;
                    double h = bounds.getHeight() * 0.5;
                    
                    WritableImage screenshot = robot.getScreenCapture(null, 
                        new Rectangle2D(x, y, w, h));
                    
                    // Update tile on JavaFX thread
                    Platform.runLater(() -> {
                        tile.setThumbnail(screenshot);
                        System.out.printf("[ImprovedScreenPicker] ✅ Window thumbnail captured: %s%n", window.title);
                    });
                } catch (Exception e) {
                    System.err.println("[ImprovedScreenPicker] Error capturing window thumbnail: " + e.getMessage());
                }
            });
        }
        
        // Update status
        updateStatus();
        
        System.out.println("[ImprovedScreenPicker] ℹ️ Thumbnails loading in background...");
    }
    
    /**
     * Create a visual tile for a screen/window source
     * Google Meet style with thumbnail preview
     */
    private SourceTile createSourceTile(DesktopSource source, boolean isWindowSource, int displayNumber) {
        SourceTile tile = new SourceTile(source, isWindowSource, displayNumber);
        
        // Click handler
        tile.setOnMouseClicked(e -> {
            selectTile(tile);
        });
        
        return tile;
    }
    
    /**
     * Select a tile and update UI
     */
    private void selectTile(SourceTile tile) {
        // Deselect previous
        if (selectedTile != null) {
            selectedTile.setSelected(false);
        }
        
        // Select new
        selectedTile = tile;
        selectedTile.setSelected(true);
        
        // Update selection
        selectedSource = tile.getSource();
        isWindow = tile.isWindow();
        
        // Enable share button
        shareButton.setDisable(false);
        
        // Update status
        String type = isWindow ? "Window" : "Screen";
        statusLabel.setText(String.format("Selected: %s - %s", type, selectedSource.title));
        
        System.out.printf("[ImprovedScreenPicker] Selected: %s (id=%d, isWindow=%b)%n", 
            selectedSource.title, selectedSource.id, isWindow);
    }
    
    /**
     * Show screens tab
     */
    private void showScreens() {
        screensContainer.setVisible(true);
        screensContainer.setManaged(true);
        windowsContainer.setVisible(false);
        windowsContainer.setManaged(false);
        
        screensTab.setStyle("-fx-background-color: #0078d4; -fx-text-fill: white;");
        windowsTab.setStyle("-fx-background-color: #3d3d3d; -fx-text-fill: white;");
    }
    
    /**
     * Show windows tab
     */
    private void showWindows() {
        screensContainer.setVisible(false);
        screensContainer.setManaged(false);
        windowsContainer.setVisible(true);
        windowsContainer.setManaged(true);
        
        screensTab.setStyle("-fx-background-color: #3d3d3d; -fx-text-fill: white;");
        windowsTab.setStyle("-fx-background-color: #0078d4; -fx-text-fill: white;");
    }
    
    /**
     * Update status label
     */
    private void updateStatus() {
        int screenCount = screensGrid.getChildren().size();
        int windowCount = windowsGrid.getChildren().size();
        
        statusLabel.setText(String.format("%d screens, %d windows available", screenCount, windowCount));
    }
    
    /**
     * Handle share button click
     */
    @FXML
    private void handleShare() {
        if (selectedSource != null) {
            confirmed = true;
            System.out.printf("[ImprovedScreenPicker] Sharing confirmed: %s%n", selectedSource.title);
            close();
        }
    }
    
    /**
     * Handle cancel button click
     */
    @FXML
    private void handleCancel() {
        confirmed = false;
        System.out.println("[ImprovedScreenPicker] Sharing cancelled");
        close();
    }
    
    /**
     * Close the dialog
     */
    private void close() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
    
    /**
     * Set the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
    
    /**
     * Check if user confirmed selection
     */
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Get selected source
     */
    public DesktopSource getSelectedSource() {
        return selectedSource;
    }
    
    /**
     * Check if selected source is a window
     */
    public boolean isWindowSelected() {
        return isWindow;
    }
    
    /**
     * Inner class representing a visual tile for a screen/window
     * Google Meet style with real thumbnail preview
     */
    private static class SourceTile extends VBox {
        private final DesktopSource source;
        private final boolean isWindow;
        private final StackPane thumbnailPane;
        private final Border selectedBorder;
        private final Border normalBorder;
        private ImageView thumbnailView;
        private Label loadingLabel;
        
        public SourceTile(DesktopSource source, boolean isWindow, int displayNumber) {
            this.source = source;
            this.isWindow = isWindow;
            
            // Styling
            setAlignment(Pos.CENTER);
            setSpacing(8);
            setPrefWidth(250);
            setStyle("-fx-cursor: hand; -fx-padding: 10; -fx-background-color: #2d2d2d; -fx-background-radius: 8;");
            
            // Borders
            selectedBorder = new Border(new BorderStroke(
                Color.web("#0078d4"), 
                BorderStrokeStyle.SOLID, 
                new CornerRadii(8), 
                new BorderWidths(3)
            ));
            
            normalBorder = new Border(new BorderStroke(
                Color.web("#3d3d3d"), 
                BorderStrokeStyle.SOLID, 
                new CornerRadii(8), 
                new BorderWidths(1)
            ));
            
            setBorder(normalBorder);
            
            // Thumbnail pane with loading indicator
            thumbnailPane = new StackPane();
            thumbnailPane.setAlignment(Pos.CENTER);
            thumbnailPane.setPrefSize(230, 130);
            thumbnailPane.setMaxSize(230, 130);
            thumbnailPane.setMinSize(230, 130);
            thumbnailPane.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 5;");
            
            // Loading indicator
            loadingLabel = new Label("Loading...");
            loadingLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
            thumbnailPane.getChildren().add(loadingLabel);
            
            // Title label
            String displayText;
            if (!isWindow && displayNumber > 0) {
                displayText = String.format("Screen %d", displayNumber);
            } else {
                displayText = source.title;
                // Truncate if too long
                if (displayText != null && displayText.length() > 30) {
                    displayText = displayText.substring(0, 27) + "...";
                }
            }
            
            Label titleLabel = new Label(displayText != null ? displayText : "Unknown");
            titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(230);
            titleLabel.setAlignment(Pos.CENTER);
            
            // Subtitle (ID info)
            Label subtitleLabel = new Label("ID: " + source.id);
            subtitleLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
            
            // Add all elements
            getChildren().addAll(thumbnailPane, titleLabel, subtitleLabel);
            
            // Hover effect
            setOnMouseEntered(e -> {
                if (getBorder() != selectedBorder) {
                    setStyle("-fx-cursor: hand; -fx-padding: 10; -fx-background-color: #3d3d3d; -fx-background-radius: 8;");
                }
            });
            
            setOnMouseExited(e -> {
                if (getBorder() != selectedBorder) {
                    setStyle("-fx-cursor: hand; -fx-padding: 10; -fx-background-color: #2d2d2d; -fx-background-radius: 8;");
                }
            });
        }
        
        /**
         * Set thumbnail from captured screenshot
         */
        public void setThumbnail(WritableImage screenshot) {
            if (screenshot == null) {
                System.err.println("[SourceTile] Null screenshot provided");
                return;
            }
            
            try {
                // Create ImageView with the screenshot
                thumbnailView = new ImageView(screenshot);
                thumbnailView.setFitWidth(230);
                thumbnailView.setFitHeight(130);
                thumbnailView.setPreserveRatio(false); // Fill the space
                thumbnailView.setSmooth(true);
                
                // Add slight border effect
                thumbnailView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);");
                
                // Replace loading indicator with thumbnail
                thumbnailPane.getChildren().clear();
                thumbnailPane.getChildren().add(thumbnailView);
                
                System.out.printf("[SourceTile] ✅ Thumbnail set for: %s%n", source.title);
                
            } catch (Exception e) {
                System.err.println("[SourceTile] Error setting thumbnail: " + e.getMessage());
                e.printStackTrace();
                
                // Show error icon
                Label errorLabel = new Label("❌");
                errorLabel.setStyle("-fx-font-size: 30px;");
                thumbnailPane.getChildren().clear();
                thumbnailPane.getChildren().add(errorLabel);
            }
        }
        
        public void setSelected(boolean selected) {
            setBorder(selected ? selectedBorder : normalBorder);
            setStyle(String.format(
                "-fx-cursor: hand; -fx-padding: 10; -fx-background-color: %s; -fx-background-radius: 8;",
                selected ? "#3d3d3d" : "#2d2d2d"
            ));
        }
        
        public DesktopSource getSource() {
            return source;
        }
        
        public boolean isWindow() {
            return isWindow;
        }
    }
}
