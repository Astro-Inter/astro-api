package com.astro.api.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseTokenService {

    public FirebaseToken verify(String idToken)
            throws FirebaseAuthException {

        return FirebaseAuth.getInstance()
                .verifyIdToken(idToken);
    }
}