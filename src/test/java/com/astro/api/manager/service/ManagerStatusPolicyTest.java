package com.astro.api.manager.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.user.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManagerStatusPolicyTest {
    final ManagerStatusPolicy policy = new ManagerStatusPolicy();
    @Test void enforcesTheTransitionMatrixWithoutCompletingPreRegistration() {
        var user = new User(); user.setFirebaseUid("uid");
        for (UserStatus current : UserStatus.values()) {
            user.setStatus(current);
            for (UserStatus desired : UserStatus.values()) {
                if (current == desired || desired == UserStatus.DESATIVADO
                        || (current == UserStatus.DESATIVADO && desired == UserStatus.ATIVO)) {
                    assertDoesNotThrow(() -> policy.validate(user, desired));
                } else assertThrows(BusinessException.class, () -> policy.validate(user, desired));
            }
        }
        user.setStatus(UserStatus.DESATIVADO); user.setFirebaseUid(null);
        assertThrows(BusinessException.class, () -> policy.validate(user, UserStatus.ATIVO));
        assertThrows(BusinessException.class, () -> policy.validate(user, null));
    }
}
