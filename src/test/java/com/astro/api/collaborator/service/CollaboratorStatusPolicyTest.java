package com.astro.api.collaborator.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollaboratorStatusPolicyTest {
    final CollaboratorStatusPolicy policy = new CollaboratorStatusPolicy();

    @Test
    void allowsDeactivationAndIdempotentRequestsIncludingUnchangedPreRegistration() {
        for (UserStatus current : UserStatus.values()) {
            User user = user(current);
            assertDoesNotThrow(() -> policy.validate(user, current));
            assertDoesNotThrow(() -> policy.validate(user, UserStatus.DESATIVADO));
        }
    }

    @Test
    void activatesOnlyCompletedAccountsWithActiveRoleAndUnit() {
        User user = user(UserStatus.DESATIVADO);
        assertDoesNotThrow(() -> policy.validate(user, UserStatus.ATIVO));
        user.setFirebaseUid(null);
        assertThrows(BusinessException.class, () -> policy.validate(user, UserStatus.ATIVO));
        user.setFirebaseUid(" ");
        assertThrows(BusinessException.class, () -> policy.validate(user, UserStatus.ATIVO));
        user.setFirebaseUid("completed");
        user.getRole().setActive(false);
        assertThrows(BusinessException.class, () -> policy.validate(user, UserStatus.ATIVO));
        user.getRole().setActive(true);
        user.getUnit().isActive = false;
        assertThrows(BusinessException.class, () -> policy.validate(user, UserStatus.ATIVO));
    }

    @Test
    void neverCompletesOrReopensPreRegistrationThroughAdministrativeStatusChanges() {
        assertThrows(BusinessException.class, () -> policy.validate(user(UserStatus.PRE_CADASTRADO), UserStatus.ATIVO));
        assertThrows(BusinessException.class, () -> policy.validate(user(UserStatus.ATIVO), UserStatus.PRE_CADASTRADO));
        assertThrows(BusinessException.class, () -> policy.validate(user(UserStatus.DESATIVADO), UserStatus.PRE_CADASTRADO));
        assertThrows(BusinessException.class, () -> policy.validate(user(UserStatus.ATIVO), null));
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setStatus(status);
        user.setFirebaseUid("completed");
        Role role = new Role(); role.setActive(true);
        user.setRole(role);
        Unit unit = new Unit(); unit.isActive = true;
        user.setUnit(unit);
        return user;
    }
}
