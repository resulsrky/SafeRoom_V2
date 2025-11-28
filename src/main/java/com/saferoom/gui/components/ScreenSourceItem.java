package com.saferoom.gui.components;

import com.saferoom.webrtc.screenshare.ScreenSourceOption;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Reusable JavaFX tile that renders a capturable source (screen/window) preview. The actual
 * look & feel is defined via {@code call-view.css}.
 */
public final class ScreenSourceItem extends VBox {

    private final ScreenSourceOption source;
    private final ImageView thumbnailView;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private boolean selected;

    public ScreenSourceItem(ScreenSourceOption source, String subtitle) {
        this.source = Objects.requireNonNull(source, "source");

        getStyleClass().add("screen-source-item");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(10);

        thumbnailView = new ImageView();
        thumbnailView.setFitWidth(220);
        thumbnailView.setFitHeight(130);
        thumbnailView.setPreserveRatio(true);

        StackPane previewWrapper = new StackPane(thumbnailView);
        previewWrapper.getStyleClass().add("screen-source-preview");

        titleLabel = new Label(source.title());
        titleLabel.getStyleClass().add("screen-source-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(220);

        subtitleLabel = new Label(subtitle != null ? subtitle : "");
        subtitleLabel.getStyleClass().add("screen-source-subtitle");

        getChildren().addAll(previewWrapper, titleLabel, subtitleLabel);

        hoverProperty().addListener((obs, oldVal, hover) -> {
            if (!selected) {
                pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), hover);
            }
        });
    }

    public ScreenSourceOption getSource() {
        return source;
    }

    public void setThumbnail(Image image) {
        if (image != null) {
            thumbnailView.setImage(image);
        }
    }

    public void setSelected(boolean value) {
        this.selected = value;
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), value);
    }
}

