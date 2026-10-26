package com.astro.api.conformity;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformity.dto.request.ConformityApplicabilityRequest;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.repository.*;
import com.astro.api.conformity.service.ConformityService;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConformityEmployeeProfilesTest {
    private final ConformityRepository conformities = mock(ConformityRepository.class);
    private final ConformityUserRepository employees = mock(ConformityUserRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NrCatalogRepository nrs = mock(NrCatalogRepository.class);
    private final ConformityService service = new ConformityService(conformities, users,
            new ConformityMapper(), employees, nrs, mock(UserAvatarService.class));
    private final AuthenticatedUser principal = new AuthenticatedUser("manager", null, Role.GESTOR);
    private final LocalDate validity = LocalDate.of(2027, 10, 26);

    @BeforeEach
    void authorize() {
        Workspace workspace = new Workspace(); workspace.id = 8L;
        Unit unit = new Unit(); unit.workspace = workspace;
        User manager = new User(); manager.setType(UserType.GESTOR);
        manager.setStatus(UserStatus.ATIVO); manager.setUnit(unit);
        when(users.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
        when(nrs.findLockedByCodeNr(10)).thenReturn(Optional.of(mock(NrCatalog.class)));
        when(conformities.saveAndFlush(any())).thenAnswer(call -> {
            Conformity saved = call.getArgument(0); saved.setId(50L); return saved;
        });
        ConformityProjection details = mock(ConformityProjection.class);
        when(details.getId()).thenReturn(50L);
        when(details.getCollaboratorId()).thenReturn(2L);
        when(conformities.findDetailsInScope(50L, "manager", 8L)).thenReturn(Optional.of(details));
    }

    private void employee(UserType type, UserStatus status) {
        User employee = new User(); employee.setId(2L);
        employee.setType(type); employee.setStatus(status);
        when(employees.findForUpdateInScope(2L, "manager", 8L)).thenReturn(Optional.of(employee));
    }

    private Conformity historicalRecord(boolean applicable) {
        Conformity record = new Conformity(); record.setId(50L);
        record.setCollaboratorId(2L); record.setNrId(10);
        record.setOrigin("MANUAL"); record.setApplicable(applicable);
        when(conformities.findRecordInScope(50L, "manager", 8L)).thenReturn(Optional.of(record));
        return record;
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void createsManualConformitiesForEveryActiveEmployeeProfile(UserType type) {
        employee(type, UserStatus.ATIVO);
        assertEquals(50L, service.create(new CreateConformityRequest(2L, 10, validity), principal).id());
        verify(employees).findForUpdateInScope(2L, "manager", 8L);
        verify(conformities).saveAndFlush(argThat(record -> record.getCollaboratorId().equals(2L)
                && record.isApplicable() && "MANUAL".equals(record.getOrigin())));
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void importsCpfForEveryActiveEmployeeProfile(UserType type) {
        employee(type, UserStatus.ATIVO);
        when(employees.findIdByCpfInScope("12345678901", "manager", 8L)).thenReturn(Optional.of(2L));
        assertTrue(service.importRow("12345678901", 10, validity, principal));
        verify(employees).findForUpdateInScope(2L, "manager", 8L);
        verify(conformities).saveAndFlush(argThat(record -> validity.equals(record.getExpiresAt())));
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void rejectsNewConformitiesAndReactivationForInactiveEmployees(UserType type) {
        employee(type, UserStatus.DESATIVADO);
        assertThrows(BusinessException.class,
                () -> service.create(new CreateConformityRequest(2L, 10, validity), principal));
        historicalRecord(false);
        assertThrows(BusinessException.class,
                () -> service.changeApplicability(50L, new ConformityApplicabilityRequest(true), principal));
        verify(conformities, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void preservesEditingOfAuthorizedInactiveEmployeeHistory(UserType type) {
        employee(type, UserStatus.DESATIVADO);
        Conformity record = historicalRecord(true);
        service.update(50L, new UpdateConformityRequest(10, validity), principal);
        assertEquals(validity, record.getExpiresAt());
        assertEquals("MANUAL", record.getOrigin());
        assertTrue(record.isApplicable());
        verify(conformities).saveAndFlush(record);
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void reactivatesConformitiesForActiveEmployeesWithoutChangingHistory(UserType type) {
        employee(type, UserStatus.ATIVO);
        Conformity record = historicalRecord(false);
        record.setExpiresAt(validity);
        service.changeApplicability(50L, new ConformityApplicabilityRequest(true), principal);
        assertTrue(record.isApplicable());
        assertEquals(validity, record.getExpiresAt());
        assertEquals("MANUAL", record.getOrigin());
    }

    @Test
    void rejectsUsersAndCpfOutsideTheAuthorizedScope() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(new CreateConformityRequest(99L, 10, validity), principal));
        assertThrows(ResourceNotFoundException.class,
                () -> service.importRow("99999999999", 10, validity, principal));
        verify(employees).findForUpdateInScope(99L, "manager", 8L);
        verify(employees).findIdByCpfInScope("99999999999", "manager", 8L);
        verify(conformities, never()).saveAndFlush(any());
    }
}
