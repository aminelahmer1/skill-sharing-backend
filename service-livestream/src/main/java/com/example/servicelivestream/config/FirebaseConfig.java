package com.example.servicelivestream.config;
/*
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() throws IOException {
        String credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH");
        InputStream serviceAccountStream;

        if (credentialsPath != null) {
            serviceAccountStream = new FileInputStream(credentialsPath);
        } else {
            serviceAccountStream = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json");
            if (serviceAccountStream == null) {
                throw new IOException("Firebase service account file not found in classpath");
            }
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}*/