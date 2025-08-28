package com.example.servicemessagerie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class FileUploadService {

    private final Path fileStorageLocation;

    // Types de fichiers autorisés
    private final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", // Images
            "mp4", "webm", "mov", "avi",         // Vidéos
            "mp3", "wav", "webm", "ogg",         // Audio
            "pdf", "doc", "docx", "txt"          // Documents
    );

    public FileUploadService(@Value("${file.upload-dir:./message-uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("✅ File upload directory created/verified: {}", this.fileStorageLocation);
        } catch (Exception ex) {
            log.error("❌ Could not create file upload directory: {}", ex.getMessage());
            throw new RuntimeException("Could not create upload directory", ex);
        }
    }

    /**
     * ✅ CORRIGÉ: Upload de fichier avec URL correcte
     */
    public String uploadFile(MultipartFile file, Long userId) throws IOException {
        // Validation du fichier
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        if (!isValidFileType(file)) {
            throw new IllegalArgumentException("File type not allowed");
        }

        if (file.getSize() > 10 * 1024 * 1024) { // 10MB max
            throw new IllegalArgumentException("File too large. Maximum size is 10MB");
        }

        try {
            // Nettoyer le nom de fichier
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            if (originalFilename.contains("..")) {
                throw new IllegalArgumentException("Invalid filename: " + originalFilename);
            }

            // Créer un nom unique
            String fileExtension = getFileExtension(originalFilename);
            String newFileName = UUID.randomUUID() + "_" + userId + "." + fileExtension;

            // Sauvegarder le fichier
            Path targetLocation = this.fileStorageLocation.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // ✅ CORRECTION: Retourner l'URL correcte avec votre configuration
            String fileUrl = "http://localhost:8822/message-uploads/" + newFileName;

            log.info("✅ File uploaded successfully: {} -> {}", originalFilename, fileUrl);
            return fileUrl;

        } catch (IOException ex) {
            log.error("❌ File upload failed: {}", ex.getMessage());
            throw new IOException("Could not store file: " + ex.getMessage(), ex);
        }
    }

    /**
     * ✅ Validation du type de fichier
     */
    public boolean isValidFileType(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return false;
        }

        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * Extrait l'extension d'un nom de fichier
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * Supprime un fichier uploadé
     */
    public boolean deleteFile(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", fileName);
            return true;
        } catch (IOException ex) {
            log.error("Could not delete file: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Vérifie si un fichier existe
     */
    public boolean fileExists(String fileName) {
        Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
        return Files.exists(filePath);
    }

    /**
     * Récupère la taille d'un fichier
     */
    public long getFileSize(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            return Files.size(filePath);
        } catch (IOException ex) {
            log.error("Could not get file size: {}", ex.getMessage());
            return -1;
        }
    }
}