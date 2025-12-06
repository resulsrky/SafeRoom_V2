package com.saferoom.gui.controller;

import com.jfoenix.controls.JFXButton;
import com.saferoom.gui.model.VaultFile;
import com.saferoom.filevault.service.DownloadsScanner;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FileVault Controller
 * 
 * SafeRoom/downloads klasöründeki tüm dosyaları listeler:
 * - DM'den gelen dosyalar
 * - Meeting'den gelen dosyalar  
 * - Kullanıcının sürükle-bırak ile eklediği dosyalar
 */
public class FileVaultController {

    @FXML private VBox dropZone;
    @FXML private TilePane fileGrid;
    @FXML private HBox fileFilterBar;

    private final ObservableList<VaultFile> allFiles = FXCollections.observableArrayList();
    private DownloadsScanner scanner;
    private String currentFilter = "All";

    @FXML
    public void initialize() {
        System.out.println("[FileVault] Initializing...");
        
        scanner = DownloadsScanner.getInstance();
        
        setupDragAndDrop();
        setupDropZoneClick();
        setupFilterButtons();

        // Downloads klasöründen dosyaları yükle
        loadFilesFromDownloads();

        // Başlangıçta "All" filtresini aktif yap
        if (!fileFilterBar.getChildren().isEmpty()) {
            fileFilterBar.getChildren().get(0).getStyleClass().add("active");
        }
        
        System.out.println("[FileVault] ✅ Ready");
    }

    /**
     * Drag & Drop ayarları
     */
    private void setupDragAndDrop() {
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                dropZone.getStyleClass().add("drag-over");
            }
            event.consume();
        });

        dropZone.setOnDragExited(event -> {
            dropZone.getStyleClass().remove("drag-over");
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            dropZone.getStyleClass().remove("drag-over");
            Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                System.out.printf("[FileVault] Dropped %d file(s)%n", files.size());
                
                for (File file : files) {
                    if (file.isFile()) {
                        addLocalFile(file.toPath());
                    }
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Drop zone'a tıklayınca dosya seçici aç
     */
    private void setupDropZoneClick() {
        dropZone.setOnMouseClicked(event -> {
            openFileChooser();
        });
    }

    /**
     * Dosya seçici dialog
     */
    private void openFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Add");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt", "*.xls", "*.xlsx"),
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
            new FileChooser.ExtensionFilter("Archives", "*.zip", "*.rar", "*.7z", "*.tar", "*.gz")
        );
        
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(dropZone.getScene().getWindow());
        if (selectedFiles != null) {
            for (File file : selectedFiles) {
                addLocalFile(file.toPath());
            }
        }
    }

    /**
     * Lokal dosya ekle (sürükle-bırak veya browse)
     */
    private void addLocalFile(Path sourcePath) {
        scanner.addLocalFile(sourcePath).thenAccept(vaultFile -> {
            if (vaultFile != null) {
                Platform.runLater(() -> {
                    allFiles.add(0, vaultFile); // En başa ekle
                    updateFileGrid(currentFilter);
                    showInfo("File Added", vaultFile.getName() + " added to vault.");
                });
            }
        });
    }

    /**
     * Downloads klasöründen dosyaları yükle
     */
    private void loadFilesFromDownloads() {
        scanner.scanAsync().thenAccept(files -> {
            Platform.runLater(() -> {
                allFiles.clear();
                allFiles.addAll(files);
                updateFileGrid(currentFilter);
                System.out.printf("[FileVault] Loaded %d files%n", files.size());
            });
        });
    }

    /**
     * Filtre butonlarını ayarla
     */
    private void setupFilterButtons() {
        for (Node node : fileFilterBar.getChildren()) {
            if (node instanceof JFXButton) {
                JFXButton button = (JFXButton) node;
                button.setOnAction(event -> {
                    // Tüm butonlardan 'active' stilini kaldır
                    fileFilterBar.getChildren().forEach(btn -> btn.getStyleClass().remove("active"));
                    // Tıklanan butona 'active' stilini ekle
                    button.getStyleClass().add("active");
                    // Filtreyi güncelle
                    currentFilter = button.getText();
                    updateFileGrid(currentFilter);
                });
            }
        }
    }

    /**
     * Dosya grid'ini güncelle
     */
    private void updateFileGrid(String filter) {
        fileGrid.getChildren().clear();
        
        List<VaultFile> filteredFiles;
        if ("All".equalsIgnoreCase(filter)) {
            filteredFiles = allFiles;
        } else {
            filteredFiles = allFiles.stream()
                    .filter(file -> file.getType().equalsIgnoreCase(filter))
                    .collect(Collectors.toList());
        }

        if (filteredFiles.isEmpty()) {
            showEmptyState();
        } else {
        for (VaultFile file : filteredFiles) {
            fileGrid.getChildren().add(createFileCard(file));
        }
    }
    }

    /**
     * Boş durum göster
     */
    private void showEmptyState() {
        VBox emptyBox = new VBox(10);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setStyle("-fx-padding: 40;");
        
        FontIcon icon = new FontIcon("fas-folder-open");
        icon.setIconSize(40);
        icon.getStyleClass().add("empty-icon");
        
        Label label = new Label("No files yet");
        label.getStyleClass().add("empty-label");
        
        emptyBox.getChildren().addAll(icon, label);
        fileGrid.getChildren().add(emptyBox);
    }

    /**
     * Dosya kartı oluştur
     */
    private VBox createFileCard(VaultFile file) {
        VBox card = new VBox(10);
        card.getStyleClass().add("file-card");

        // Üst Kısım: İkon ve Butonlar
        HBox topPane = new HBox();
        topPane.setAlignment(Pos.CENTER_LEFT);
        
        // Dosya ikonu (kategoriye göre)
        FontIcon fileIcon = new FontIcon(getIconForFile(file));
        fileIcon.getStyleClass().add("file-icon");
        
        // Şifreli ise kilit ikonu ekle
        if (file.isEncrypted()) {
            FontIcon lockIcon = new FontIcon("fas-lock");
            lockIcon.getStyleClass().add("lock-icon");
            topPane.getChildren().add(lockIcon);
        }

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Aksiyon butonları
        HBox actions = new HBox(5);
        actions.setAlignment(Pos.CENTER);
        
        // Aç butonu
        Button openButton = new Button("", new FontIcon("fas-external-link-alt"));
        openButton.getStyleClass().add("file-action-button");
        openButton.setTooltip(new Tooltip("Open"));
        openButton.setOnAction(e -> openFile(file));
        
        // Şifrele/Çöz butonu
        Button encryptButton = new Button("", new FontIcon(file.isEncrypted() ? "fas-unlock" : "fas-lock"));
        encryptButton.getStyleClass().add("file-action-button");
        encryptButton.setTooltip(new Tooltip(file.isEncrypted() ? "Decrypt" : "Encrypt"));
        encryptButton.setOnAction(e -> toggleEncryption(file));
        
        // Sil butonu
        Button deleteButton = new Button("", new FontIcon("fas-trash-alt"));
        deleteButton.getStyleClass().add("file-action-button");
        deleteButton.setTooltip(new Tooltip("Delete"));
        deleteButton.setOnAction(e -> deleteFile(file));
        
        actions.getChildren().addAll(openButton, encryptButton, deleteButton);

        topPane.getChildren().addAll(fileIcon, spacer, actions);

        // Orta Kısım: Dosya Adı
        Label fileName = new Label(file.getName());
        fileName.getStyleClass().add("file-name");
        fileName.setWrapText(true);
        fileName.setMaxWidth(180);

        // Alt Kısım: Dosya Bilgileri
        HBox metaPane = new HBox();
        metaPane.setAlignment(Pos.CENTER_LEFT);
        
        Label fileSize = new Label(file.getSize());
        fileSize.getStyleClass().add("file-meta");
        
        Pane metaSpacer = new Pane();
        HBox.setHgrow(metaSpacer, Priority.ALWAYS);
        
        Label fileDate = new Label(file.getDate());
        fileDate.getStyleClass().add("file-meta");
        
        // Kaynak göster (DM/Meeting/Local)
        Label sourceLabel = new Label(file.getSource().toUpperCase());
        sourceLabel.getStyleClass().addAll("source-badge", "source-" + file.getSource());
        
        metaPane.getChildren().addAll(fileSize, metaSpacer, sourceLabel);

        card.getChildren().addAll(topPane, fileName, metaPane);
        return card;
    }

    /**
     * Dosya tipine göre ikon
     */
    private String getIconForFile(VaultFile file) {
        String category = file.getType();
        
        if ("Images".equalsIgnoreCase(category)) {
            return "fas-file-image";
        } else if ("Archives".equalsIgnoreCase(category)) {
            return "fas-file-archive";
        } else {
            // Documents için uzantıya göre
            String name = file.getName().toLowerCase();
            if (name.endsWith(".pdf")) return "fas-file-pdf";
            if (name.endsWith(".doc") || name.endsWith(".docx")) return "fas-file-word";
            if (name.endsWith(".xls") || name.endsWith(".xlsx")) return "fas-file-excel";
            if (name.endsWith(".ppt") || name.endsWith(".pptx")) return "fas-file-powerpoint";
            if (name.endsWith(".txt") || name.endsWith(".md")) return "fas-file-alt";
            return "fas-file";
        }
    }

    /**
     * Dosyayı aç
     */
    private void openFile(VaultFile file) {
        try {
            Path filePath = file.getPath();
            if (filePath != null && Files.exists(filePath)) {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("linux")) {
                    pb = new ProcessBuilder("xdg-open", filePath.toString());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", filePath.toString());
                } else {
                    pb = new ProcessBuilder("cmd", "/c", "start", "", filePath.toString());
                }
                pb.start();
            } else {
                showError("File Not Found", "The file no longer exists.");
            }
        } catch (Exception e) {
            showError("Error", "Could not open file: " + e.getMessage());
        }
    }

    /**
     * Şifreleme toggle
     */
    private void toggleEncryption(VaultFile file) {
        if (file.isEncrypted()) {
            // Decrypt
            showInfo("Decrypt", "Decryption feature coming soon!");
        } else {
            // Encrypt
            showInfo("Encrypt", "Encryption feature coming soon!");
        }
        // TODO: AES-256 şifreleme implementasyonu
    }

    /**
     * Dosya sil
     */
    private void deleteFile(VaultFile file) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete File");
        confirm.setHeaderText("Delete " + file.getName() + "?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (scanner.deleteFile(file)) {
                    allFiles.remove(file);
                    updateFileGrid(currentFilter);
                } else {
                    showError("Error", "Could not delete file.");
                }
            }
        });
    }

    /**
     * Dosyaları yenile (dışarıdan çağrılabilir)
     */
    public void refresh() {
        Platform.runLater(this::loadFilesFromDownloads);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
