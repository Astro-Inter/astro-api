package com.astro.api.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Date;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("conformity-local")
public class LocalFirebaseConfig {
    @Bean
    public FirebaseApp firebaseApp() {
        if (!java.util.Set.of("127.0.0.1:9167", "astro657-auth:9167").contains(System.getenv("FIREBASE_AUTH_EMULATOR_HOST"))) {
            throw new IllegalStateException("O perfil conformity-local exige o emulador Firebase em 127.0.0.1:9167");
        }
        FirebaseOptions options = FirebaseOptions.builder().setProjectId("demo-astro657")
                .setCredentials(GoogleCredentials.create(new AccessToken("local-emulator", new Date(Long.MAX_VALUE)))).build();
        return FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
    }
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) { return FirebaseAuth.getInstance(app); }
}
