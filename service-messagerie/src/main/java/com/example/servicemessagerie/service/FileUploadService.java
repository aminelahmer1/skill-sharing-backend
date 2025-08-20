package com.example.servicemessagerie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    @Value("${file.upload-dir:./message-uploads}")
    private String uploadDir;

    @Value("${file.max-size:10MB}")
    private DataSize maxFileSize;

    public String uploadFile(MultipartFile file, Long userId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxFileSize.toBytes()) {  // Convert to bytes
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }

        // Créer le répertoire s'il n'existe pas
        Path uploadPath = Paths.get(uploadDir, userId.toString());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Générer un nom unique pour le fichier
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // Sauvegarder le fichier
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.write(filePath, file.getBytes());

        log.info("File uploaded successfully: {}", filePath);

        // Retourner l'URL relative
        return "/message-uploads/" + userId + "/" + uniqueFilename;
    }

    public void deleteFile(String fileUrl) {
        try {
            Path filePath = Paths.get(uploadDir + fileUrl.replace("/message-uploads", ""));
            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", filePath);
        } catch (IOException e) {
            log.error("Error deleting file: {}", fileUrl, e);
        }
    }

    public boolean isValidFileType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.startsWith("image/") ||
                        contentType.startsWith("video/") ||
                        contentType.startsWith("audio/") ||
                        contentType.equals("application/pdf") ||
                        contentType.equals("application/msword") ||
                        contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
    }
}