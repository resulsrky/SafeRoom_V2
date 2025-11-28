package com.saferoom.gui.controller;

import com.saferoom.gui.dialog.ScreenSourcePickerDialog;
import com.saferoom.webrtc.screenshare.ScreenShareController;
import com.saferoom.webrtc.screenshare.ScreenSourceOption;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the call controls (mute/video/share). Handles the screen-share lifecycle glue
 * between UI and {@link ScreenShareController}.
 */
public final class CallView {

    private static final Logger LOGGER = Logger.getLogger(CallView.class.getName());

    @FXML
    private Button shareButton;

    @FXML
    private Label sharingBanner;

    private ScreenShareController screenShareController;
    private boolean pendingShareAction;

    public void setScreenShareController(ScreenShareController controller) {
        this.screenShareController = controller;
        if (shareButton != null) {
            shareButton.setDisable(pendingShareAction || controller == null);
        }
        updateShareState();
    }

    @FXML
    private void initialize() {
        updateShareState();
    }

    @FXML
    private void handleShareButton(ActionEvent event) {
        if (screenShareController == null) {
            LOGGER.warning("ScreenShareController not attached; ignoring share click");
            return;
        }

        if (screenShareController.isSharing()) {
            stopSharing();
        } else {
            startSharing();
        }
    }

    private void startSharing() {
        Window owner = shareButton.getScene() != null ? shareButton.getScene().getWindow() : null;
        shareButton.setDisable(true);
        try (ScreenSourcePickerDialog dialog = new ScreenSourcePickerDialog(screenShareController)) {
            Optional<ScreenSourceOption> selection = dialog.showAndWait(owner);
            if (selection.isEmpty()) {
                shareButton.setDisable(screenShareController == null);
                updateShareState();
                return;
            }
            setShareButtonBusy(true, "Starting…");
            CompletableFuture<Void> future = screenShareController.startScreenShare(selection.get());
            future.whenComplete((ignored, error) -> Platform.runLater(() -> {
                setShareButtonBusy(false, "Share Screen");
                if (error != null) {
                    LOGGER.log(Level.WARNING, "Failed to start screen share", error);
                    showError("Unable to start screen share", error);
                }
                updateShareState();
            }));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Screen picker dialog failed", e);
            showError("Unable to open screen picker", e);
        } finally {
            if (!pendingShareAction) {
                shareButton.setDisable(screenShareController == null);
            }
        }
    }

    private void stopSharing() {
        setShareButtonBusy(true, "Stopping…");
        screenShareController.stopScreenShare()
            .whenComplete((ignored, error) -> Platform.runLater(() -> {
                setShareButtonBusy(false, "Share Screen");
                if (error != null) {
                    LOGGER.log(Level.WARNING, "Failed to stop screen share", error);
                    showError("Unable to stop screen share", error);
                }
                updateShareState();
            }));
    }

    private void updateShareState() {
        if (shareButton == null) {
            return;
        }
        boolean sharing = screenShareController != null && screenShareController.isSharing();
        sharingBanner.setVisible(sharing);
        sharingBanner.setManaged(sharing);
        shareButton.getStyleClass().remove("active");
        if (sharing) {
            shareButton.getStyleClass().add("active");
            shareButton.setText("Stop Sharing");
        } else {
            shareButton.setText("Share Screen");
        }
        shareButton.setDisable(pendingShareAction || screenShareController == null);
    }

    private void setShareButtonBusy(boolean busy, String textWhenIdle) {
        pendingShareAction = busy;
        shareButton.setDisable(busy || screenShareController == null);
        if (busy) {
            shareButton.setText("Please wait…");
        } else {
            shareButton.setText(textWhenIdle);
        }
    }

    private void showError(String title, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Screen Share");
        alert.setHeaderText(title);
        alert.setContentText(error.getMessage());
        alert.show();
    }
}

