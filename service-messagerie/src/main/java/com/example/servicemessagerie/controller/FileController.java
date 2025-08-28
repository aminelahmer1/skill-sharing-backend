package com.example.servicemessagerie.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@CrossOrigin(origins = "http://localhost:4200")
public class FileController {

    private final Path messageFileStorageLocation;

    public FileController(@Value("${file.upload-dir:./message-uploads}") String uploadDir) {
        this.messageFileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Sert les fichiers uploadés pour les messages
     */
    @GetMapping("/message-uploads/{fileName:.+}")
    public ResponseEntity<Resource> downloadMessageFile(@PathVariable String fileName) {
        try {
            Path filePath = this.messageFileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not found or not readable: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            // Déterminer le type de contenu
            String contentType = null;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException ex) {
                log.warn("Could not determine file type for: {}", fileName);
            }

            // Fallback pour les types de contenu courants
            if (contentType == null) {
                contentType = determineContentType(fileName);
            }

            // Headers pour le téléchargement
            HttpHeaders headers = new HttpHeaders();

            // Pour les images, vidéos et audio, afficher inline
            if (contentType != null && (contentType.startsWith("image/") ||
                    contentType.startsWith("video/") ||
                    contentType.startsWith("audio/"))) {
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
            } else {
                // Pour les autres fichiers, forcer le téléchargement
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            }

            log.debug("Serving file: {} with content-type: {}", fileName, contentType);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                    .headers(headers)
                    .body(resource);

        } catch (MalformedURLException ex) {
            log.error("File path is invalid: {}", fileName);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Endpoint pour vérifier si un fichier existe
     */
    @RequestMapping(value = "/message-uploads/{fileName:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> checkMessageFile(@PathVariable String fileName) {
        try {
            Path filePath = this.messageFileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Détermine le type de contenu basé sur l'extension
     */
    private String determineContentType(String fileName) {
        String extension = getFileExtension(fileName);

        switch (extension) {
            // Images
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";

            // Vidéos
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
            case "mov":
                return "video/quicktime";
            case "avi":
                return "video/x-msvideo";

            // Audio
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "ogg":
                return "audio/ogg";

            // Documents
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt":
                return "text/plain";

            default:
                return "application/octet-stream";
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }

        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
}