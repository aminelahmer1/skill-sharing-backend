package com.example.servicelivestream.service;

import com.example.servicelivestream.config.LiveKitConfig;
import com.example.servicelivestream.exception.LiveKitOperationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitService {
    private final LiveKitConfig liveKitConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map pour stocker les IDs d'egress par room
    private final Map<String, String> activeRecordings = new ConcurrentHashMap<>();

    // Map pour simuler l'enregistrement en développement
    private final Map<String, RecordingSimulation> simulatedRecordings = new ConcurrentHashMap<>();

    public String generateToken(String userId, String roomName, boolean isPublisher) {
        try {
            validateParameters(userId, roomName);
            log.debug("Generating token for user: {}, room: {}, isPublisher: {}", userId, roomName, isPublisher);
            return buildToken(userId, roomName, isPublisher);
        } catch (JwtException e) {
            log.error("JWT generation failed", e);
            throw new LiveKitOperationException("Token generation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during token generation", e);
            throw new LiveKitOperationException("Token operation failed", e);
        }
    }

    private String buildToken(String userId, String roomName, boolean isPublisher) {
        byte[] keyBytes = liveKitConfig.getApiSecret().getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        Map<String, Object> header = new HashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "HS256");

        long now = System.currentTimeMillis() / 1000;

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("name", userId);
        claims.put("iss", liveKitConfig.getApiKey());
        claims.put("nbf", now);
        claims.put("exp", now + (isPublisher ?
                liveKitConfig.getToken().getPublisherTtl() :
                liveKitConfig.getToken().getTtl()));

        claims.put("video", createVideoGrant(roomName, isPublisher));

        return Jwts.builder()
                .setHeader(header)
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private Map<String, Object> createVideoGrant(String roomName, boolean isPublisher) {
        Map<String, Object> grant = new HashMap<>();
        grant.put("room", roomName);
        grant.put("roomJoin", true);
        grant.put("canSubscribe", true);
        grant.put("canPublishData", true);
        grant.put("canUpdateOwnMetadata", true);
        grant.put("hidden", false);
        grant.put("recorder", false);

        if (isPublisher) {
            grant.put("canPublish", true);
            grant.put("canPublishSources", new String[]{"camera", "microphone", "screen_share"});
        } else {
            grant.put("canPublish", true);
            grant.put("canPublishSources", new String[]{"camera", "microphone"});
        }

        return grant;
    }

    /**
     * Démarre l'enregistrement d'une room.
     * En développement : simule l'enregistrement avec un fichier de test
     * En production : utilise l'API Egress de LiveKit
     */
    public void startRoomRecording(String roomName, String outputPath) {
        try {
            log.info("Starting recording for room: {} to path: {}", roomName, outputPath);

            // Vérifier si on est en mode développement
            if (isEgressAvailable()) {
                // Production : Utiliser l'API Egress
                startEgressRecording(roomName, outputPath);
            } else {
                // Développement : Simuler l'enregistrement
                simulateRecording(roomName, outputPath);
            }

        } catch (Exception e) {
            log.error("Failed to start recording for room: {}", roomName, e);
            throw new LiveKitOperationException("Failed to start recording: " + e.getMessage(), e);
        }
    }

    /**
     * Arrête l'enregistrement d'une room
     */
    public void stopRoomRecording(String roomName) {
        try {
            log.info("Stopping recording for room: {}", roomName);

            if (isEgressAvailable()) {
                // Production : Arrêter via l'API Egress
                stopEgressRecording(roomName);
            } else {
                // Développement : Arrêter la simulation
                stopSimulatedRecording(roomName);
            }

        } catch (Exception e) {
            log.error("Failed to stop recording for room: {}", roomName, e);
            throw new LiveKitOperationException("Failed to stop recording: " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie si le service Egress est disponible
     */
    private boolean isEgressAvailable() {
        // En développement, on peut vérifier si Egress est accessible
        // Pour simplifier, on va supposer qu'on est en dev si l'URL contient localhost
        return !liveKitConfig.getServerUrl().contains("localhost");
    }

    /**
     * Démarre l'enregistrement via l'API Egress (production)
     */
    private void startEgressRecording(String roomName, String outputPath) {
        try {
            String egressUrl = liveKitConfig.getServerUrl() + "/twirp/livekit.Egress/StartRoomCompositeEgress";

            Map<String, Object> request = new HashMap<>();
            request.put("room_name", roomName);
            request.put("layout", "speaker");
            request.put("audio_only", false);
            request.put("video_only", false);

            // Configuration du fichier de sortie
            Map<String, Object> file = new HashMap<>();
            file.put("filepath", outputPath);
            request.put("file", file);

            // Configuration de l'encodage
            Map<String, Object> preset = new HashMap<>();
            preset.put("encoding", "H264_720P_30");
            request.put("preset", preset);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + generateAdminToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    egressUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String egressId = (String) response.getBody().get("egress_id");
                activeRecordings.put(roomName, egressId);
                log.info("Egress recording started with ID: {}", egressId);
            }

        } catch (Exception e) {
            log.error("Failed to start Egress recording", e);
            throw new LiveKitOperationException("Failed to start Egress recording", e);
        }
    }

    /**
     * Arrête l'enregistrement via l'API Egress
     */
    private void stopEgressRecording(String roomName) {
        String egressId = activeRecordings.get(roomName);
        if (egressId == null) {
            log.warn("No active recording found for room: {}", roomName);
            return;
        }

        try {
            String egressUrl = liveKitConfig.getServerUrl() + "/twirp/livekit.Egress/StopEgress";

            Map<String, Object> request = new HashMap<>();
            request.put("egress_id", egressId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + generateAdminToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    egressUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                activeRecordings.remove(roomName);
                log.info("Egress recording stopped for room: {}", roomName);
            }

        } catch (Exception e) {
            log.error("Failed to stop Egress recording", e);
        }
    }

    /**
     * Simule l'enregistrement en créant un fichier de test (développement)
     */
    private void simulateRecording(String roomName, String outputPath) {
        try {
            Path recordingPath = Paths.get(outputPath);
            Files.createDirectories(recordingPath.getParent());

            RecordingSimulation simulation = new RecordingSimulation();
            simulation.roomName = roomName;
            simulation.outputPath = outputPath;
            simulation.startTime = System.currentTimeMillis();
            simulation.isActive = true;

            simulatedRecordings.put(roomName, simulation);

            // Créer un fichier vidéo de test réaliste
            createRealisticVideoFile(recordingPath, 30); // 30 secondes par défaut

            log.info("Simulated recording started for room: {} at {}", roomName, outputPath);

        } catch (Exception e) {
            log.error("Failed to create simulated recording", e);
            throw new LiveKitOperationException("Failed to create simulated recording", e);
        }
    }

    private void createRealisticVideoFile(Path filePath, int durationSeconds) throws IOException {
        // Vérifier si FFmpeg est disponible pour créer de vraies vidéos
        if (isFfmpegAvailable()) {
            createVideoWithFfmpeg(filePath, durationSeconds);
        } else {
            // Fallback: créer un fichier vidéo réaliste sans FFmpeg
            createRealisticVideoFallback(filePath, durationSeconds);
        }
    }

    private boolean isFfmpegAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("ffmpeg -version");
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void createVideoWithFfmpeg(Path filePath, int durationSeconds) throws IOException {
        try {
            String[] command = {
                    "ffmpeg",
                    "-f", "lavfi",
                    "-i", "testsrc=s=1280x720:r=30:d=" + durationSeconds,
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-pix_fmt", "yuv420p",
                    "-y",
                    filePath.toString()
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            // Attendre la fin du processus avec timeout
            boolean finished = process.waitFor(durationSeconds + 10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroy();
                throw new IOException("FFmpeg timeout");
            }

            if (process.exitValue() != 0) {
                throw new IOException("FFmpeg failed with exit code: " + process.exitValue());
            }

            log.info("Created real video with FFmpeg: {}", filePath);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg interrupted", e);
        } catch (Exception e) {
            throw new IOException("FFmpeg execution failed", e);
        }
    }

    private void createRealisticVideoFallback(Path filePath, int durationSeconds) throws IOException {
        // Créer un fichier vidéo réaliste sans FFmpeg
        // La taille varie selon la durée pour simuler une vraie vidéo
        long fileSize = calculateRealisticFileSize(durationSeconds);

        try (FileChannel channel = FileChannel.open(filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Écrire un header MP4 valide
            byte[] mp4Header = createValidMp4Header();
            channel.write(ByteBuffer.wrap(mp4Header));

            // Ajouter des données vidéo simulées
            long remainingSize = fileSize - mp4Header.length;
            if (remainingSize > 0) {
                writeVideoData(channel, remainingSize);
            }
        }

        log.info("Created realistic video file: {} ({} bytes)", filePath, Files.size(filePath));
    }

    private long calculateRealisticFileSize(int durationSeconds) {
        // Taille réaliste basée sur la durée : ~2MB par minute
        return (durationSeconds * 2 * 1024 * 1024) / 60;
    }

    private byte[] createValidMp4Header() {
        // Header MP4 minimal mais valide
        return new byte[] {
                // ftyp box
                0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
                0x6D, 0x70, 0x34, 0x32, 0x00, 0x00, 0x00, 0x00,
                0x6D, 0x70, 0x34, 0x32, 0x69, 0x73, 0x6F, 0x6D,
                // mdat box header
                0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x61, 0x74
        };
    }

    private void writeVideoData(FileChannel channel, long size) throws IOException {
        // Écrire des données vidéo simulées
        byte[] buffer = new byte[8192];
        Random random = new Random();
        long written = 0;

        while (written < size) {
            random.nextBytes(buffer);
            int toWrite = (int) Math.min(buffer.length, size - written);
            channel.write(ByteBuffer.wrap(buffer, 0, toWrite));
            written += toWrite;
        }
    }

    private void stopSimulatedRecording(String roomName) {
        RecordingSimulation simulation = simulatedRecordings.get(roomName);
        if (simulation == null) {
            log.warn("No simulated recording found for room: {}", roomName);
            return;
        }

        try {
            long duration = System.currentTimeMillis() - simulation.startTime;
            int durationSeconds = (int) (duration / 1000);

            Path recordingPath = Paths.get(simulation.outputPath);

            if (Files.exists(recordingPath)) {
                // Si FFmpeg a été utilisé, le fichier est déjà complet
                if (!isFfmpegAvailable()) {
                    // Ajuster la taille du fichier pour correspondre à la durée réelle
                    adjustFileSizeForDuration(recordingPath, durationSeconds);
                }

                log.info("Recording completed: {} (duration: {}s, size: {} bytes)",
                        recordingPath, durationSeconds, Files.size(recordingPath));
            }

            simulation.isActive = false;
            simulatedRecordings.remove(roomName);

        } catch (IOException e) {
            log.error("Failed to finalize simulated recording", e);
        }
    }

    private void adjustFileSizeForDuration(Path filePath, int durationSeconds) throws IOException {
        long targetSize = calculateRealisticFileSize(durationSeconds);
        long currentSize = Files.size(filePath);

        if (currentSize < targetSize) {
            // Agrandir le fichier
            try (FileChannel channel = FileChannel.open(filePath,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {

                Random random = new Random();
                byte[] buffer = new byte[8192];
                long remaining = targetSize - currentSize;

                while (remaining > 0) {
                    random.nextBytes(buffer);
                    int toWrite = (int) Math.min(buffer.length, remaining);
                    channel.write(ByteBuffer.wrap(buffer, 0, toWrite));
                    remaining -= toWrite;
                }
            }
        } else if (currentSize > targetSize) {
            // Réduire le fichier
            try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
                raf.setLength(targetSize);
            }
        }
    }
    private byte[] getMinimalValidMp4() {
        try {
            // Essayer de lire un fichier de test s'il existe
            Path testVideoPath = Paths.get("src/main/resources/test-video.mp4");
            if (Files.exists(testVideoPath)) {
                return Files.readAllBytes(testVideoPath);
            }

            // Sinon, créer un header MP4 minimal
            return createBasicMp4Header();
        } catch (IOException e) {
            log.warn("Could not read test video file: {}", e.getMessage());
            return createBasicMp4Header();
        }
    }

    private byte[] createBasicMp4Header() {
        // Header MP4 minimal qui sera reconnu comme fichier vidéo
        // Ce n'est pas une vidéo lisible mais le fichier sera reconnu comme MP4
        byte[] header = new byte[1024]; // 1KB de données
        // Ajouter la signature MP4 au début
        System.arraycopy("ftypmp42".getBytes(StandardCharsets.UTF_8), 0, header, 4, 8);
        // Définir la taille du box
        header[0] = 0x00;
        header[1] = 0x00;
        header[2] = 0x04;
        header[3] = 0x00; // 1024 bytes

        return header;
    }

    private void createEmptyFileAsFallback(Path filePath) {
        try {
            // Dernier recours: créer un fichier vide avec l'extension correcte
            Files.write(filePath, new byte[0]);
            log.warn("Created empty file as fallback: {}", filePath);
        } catch (IOException ex) {
            log.error("Complete failure: could not create any file at: {}", filePath);
            throw new LiveKitOperationException("Failed to create recording file at: " + filePath, ex);
        }
    }


    private byte[] createMinimalMp4Header() {
        // Ceci est un header MP4 minimal qui rendra le fichier reconnaissable comme MP4
        // mais ne sera pas lisible comme vidéo réelle
        return new byte[] {
                0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, // ftyp box
                0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x02, 0x00,
                0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32,
                0x61, 0x76, 0x63, 0x31, 0x6D, 0x70, 0x34, 0x31
        };
    }

    /**
     * Génère un token admin pour les appels API
     */
    private String generateAdminToken() {
        byte[] keyBytes = liveKitConfig.getApiSecret().getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        long now = System.currentTimeMillis() / 1000;

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", liveKitConfig.getApiKey());
        claims.put("nbf", now);
        claims.put("exp", now + 3600); // 1 heure
        claims.put("video", new HashMap<String, Object>() {{
            put("roomAdmin", true);
            put("roomRecord", true);
        }});

        return Jwts.builder()
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public void validateParameters(String userId, String roomName) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("Room name cannot be null or empty");
        }
        if (liveKitConfig.getApiKey() == null || liveKitConfig.getApiKey().isBlank()) {
            throw new IllegalStateException("LiveKit API key is not configured");
        }
        if (liveKitConfig.getApiSecret() == null || liveKitConfig.getApiSecret().isBlank()) {
            throw new IllegalStateException("LiveKit API secret is not configured");
        }




    }
    private boolean checkFfmpegAvailability() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor(3, TimeUnit.SECONDS);
            return line != null && line.contains("ffmpeg");
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * Classe interne pour stocker les informations de simulation
     */
    private static class RecordingSimulation {
        String roomName;
        String outputPath;
        long startTime;
        boolean isActive;
        boolean usedFfmpeg;
    }
}