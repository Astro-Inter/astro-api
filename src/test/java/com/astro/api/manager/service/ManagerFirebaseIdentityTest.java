package com.astro.api.manager.service;

import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.ConflictException;
import com.google.firebase.auth.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerFirebaseIdentityTest {
    @Test void readsUpdatesAndRestoresTheExistingAccountWithoutCreatingOne() throws Exception {
        var auth = mock(FirebaseAuth.class); var user = mock(UserRecord.class);
        when(auth.getUser("uid")).thenReturn(user); when(user.getEmail()).thenReturn("old@astro.com"); when(user.isEmailVerified()).thenReturn(true);
        var service = new FirebaseIdentityService(auth); var previous = service.readEmailState("uid");
        assertEquals(new FirebaseIdentityService.EmailState("old@astro.com", true), previous);
        service.updateEmail("uid", "new@astro.com"); service.restoreEmail("uid", previous);
        verify(auth, times(2)).updateUser(any(UserRecord.UpdateRequest.class));
        verify(auth, never()).createUser(any());
        var error = mock(FirebaseAuthException.class); when(error.getAuthErrorCode()).thenReturn(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        when(auth.updateUser(any())).thenThrow(error);
        assertThrows(ConflictException.class, () -> service.updateEmail("uid", "taken@astro.com"));
        assertThrows(IllegalStateException.class, () -> service.restoreEmail("uid", previous));
    }
}
