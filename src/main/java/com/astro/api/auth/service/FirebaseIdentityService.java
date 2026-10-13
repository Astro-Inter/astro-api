package com.astro.api.auth.service;

import com.astro.api.common.exception.ConflictException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FirebaseIdentityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseIdentityService.class);
    private final FirebaseAuth firebaseAuth;

    public FirebaseIdentityService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public String createUser(String email, String password, String displayName) {
        try {
            UserRecord user = firebaseAuth.createUser(new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(displayName));
            return user.getUid();
        } catch (FirebaseAuthException exception) {
            if (exception.getAuthErrorCode() != null
                    && "EMAIL_ALREADY_EXISTS".equals(exception.getAuthErrorCode().name())) {
                throw new ConflictException("Já existe uma conta Firebase para este e-mail");
            }
            throw new IllegalStateException("Não foi possível criar a conta Firebase", exception);
        }
    }

    public void compensateCreatedUser(String firebaseUid) {
        try {
            firebaseAuth.deleteUser(firebaseUid);
        } catch (FirebaseAuthException exception) {
            LOGGER.error("Falha ao compensar a identidade Firebase criada durante o cadastro (uid={})", firebaseUid, exception);
        }
    }

    public EmailState readEmailState(String firebaseUid) {
        try {
            UserRecord user = firebaseAuth.getUser(firebaseUid);
            return new EmailState(user.getEmail(), user.isEmailVerified());
        } catch (FirebaseAuthException exception) {
            throw new IllegalStateException("Não foi possível consultar a identidade Firebase", exception);
        }
    }

    public void updateEmail(String firebaseUid, String email) {
        try {
            firebaseAuth.updateUser(new UserRecord.UpdateRequest(firebaseUid).setEmail(email).setEmailVerified(false));
        } catch (FirebaseAuthException exception) {
            if (exception.getAuthErrorCode() != null
                    && "EMAIL_ALREADY_EXISTS".equals(exception.getAuthErrorCode().name())) {
                throw new ConflictException("Já existe uma conta Firebase para este e-mail");
            }
            throw new IllegalStateException("Não foi possível atualizar o e-mail no Firebase", exception);
        }
    }

    public void restoreEmail(String firebaseUid, EmailState previous) {
        try {
            firebaseAuth.updateUser(new UserRecord.UpdateRequest(firebaseUid)
                    .setEmail(previous.email()).setEmailVerified(previous.verified()));
        } catch (FirebaseAuthException exception) {
            throw new IllegalStateException("Não foi possível compensar o e-mail no Firebase", exception);
        }
    }

    public record EmailState(String email, boolean verified) { }
}
