package com.rental.user.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "notifications.push.enabled", havingValue = "true")
public class FirebaseConfig {
    @Bean
    FirebaseApp firebaseApp(@Value("${FCM_PROJECT_ID:}") String projectId,
            @Value("${FCM_SERVICE_ACCOUNT_JSON:}") String serviceAccountJson) throws IOException {
        GoogleCredentials credentials = serviceAccountJson == null || serviceAccountJson.isBlank()
                ? GoogleCredentials.getApplicationDefault()
                : GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        if (projectId != null && !projectId.isBlank()) options.setProjectId(projectId);
        return FirebaseApp.initializeApp(options.build());
    }
}
