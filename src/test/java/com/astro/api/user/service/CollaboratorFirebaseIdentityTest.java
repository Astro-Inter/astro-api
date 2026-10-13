package com.astro.api.user.service;

import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.ConflictException;
import com.google.firebase.auth.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaboratorFirebaseIdentityTest {
    @Test
    void readsThePreviousEmailAndVerificationStateForCompensation() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        UserRecord user = mock(UserRecord.class);
        when(user.getEmail()).thenReturn("old@example.com");
        when(user.isEmailVerified()).thenReturn(true);
        when(auth.getUser("completed")).thenReturn(user);
        var service = new FirebaseIdentityService(auth);
        var state = service.readEmailState("completed");
        assertEquals(new FirebaseIdentityService.EmailState("old@example.com", true), state);
        service.updateEmail("completed", "new@example.com");
        service.restoreEmail("completed", state);
        verify(auth, times(2)).updateUser(any(UserRecord.UpdateRequest.class));
        verify(auth, never()).createUser(any());
    }

    @Test
    void translatesKnownFirebaseConflictsAndReportsUnavailableUpdates() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseAuthException conflict = mock(FirebaseAuthException.class);
        when(conflict.getAuthErrorCode()).thenReturn(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        when(auth.updateUser(any())).thenThrow(conflict);
        var service = new FirebaseIdentityService(auth);
        assertThrows(ConflictException.class, () -> service.updateEmail("completed", "new@example.com"));
        FirebaseAuthException failure = mock(FirebaseAuthException.class);
        doThrow(failure).when(auth).updateUser(any());
        assertThrows(IllegalStateException.class, () -> service.updateEmail("completed", "new@example.com"));
        assertThrows(IllegalStateException.class, () -> service.restoreEmail("completed", new FirebaseIdentityService.EmailState("old@example.com", true)));
    }
}
