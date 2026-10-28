package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.common.exception.*;
import com.astro.api.conformity.dto.request.*;
import com.astro.api.conformity.mapper.UnitConformityMapper;
import com.astro.api.conformity.model.*;
import com.astro.api.conformity.repository.*;
import com.astro.api.conformity.service.UnitConformityService;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitConformityServiceTest {
    final ConformityRepository writes = mock(ConformityRepository.class);
    final UnitConformityRepository records = mock(UnitConformityRepository.class);
    final NrCatalogRepository nrs = mock(NrCatalogRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final UnitConformityService service = new UnitConformityService(writes,records,users,nrs,new UnitConformityMapper());
    final AuthenticatedUser principal = new AuthenticatedUser("manager","manager@astro.local",Role.GESTOR);
    void setup() {
        Unit unit = new Unit(); unit.id=2L; unit.isActive=true; unit.workspace=new Workspace(); unit.workspace.id=1L;
        User manager = new User(); manager.setUnit(unit); manager.setType(UserType.GESTOR); manager.setStatus(UserStatus.ATIVO);
        when(users.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
        when(records.findUnitInScope(2L,1L,"manager")).thenReturn(Optional.of(unit));
        when(nrs.findLockedByCodeNr(1)).thenReturn(Optional.of(mock(NrCatalog.class)));
    }
    @Test void requiresValidatedIdentity() { assertThrows(AccessDeniedException.class,()->service.findAll(null)); verifyNoInteractions(records); }
    @Test void foreignWorkspaceIsNotFound() {
        setup(); assertThrows(ResourceNotFoundException.class,()->service.create(new CreateUnitConformityRequest(3L,1,null),principal));
        verify(records,never()).saveAndFlush(any());
    }
    @Test void manualCreationRejectsExistingApplicablePair() {
        setup(); when(records.hasApplicableConflict(2L,1,null)).thenReturn(true);
        assertThrows(ConflictException.class,()->service.create(new CreateUnitConformityRequest(2L,1,null),principal));
        verify(records,never()).saveAndFlush(any());
    }
    @Test void linkedFormNrCannotBeReplaced() {
        setup(); Conformity record=new Conformity(); record.setUnitId(2L); record.setNrId(1); record.setFormResponseId("form-657");
        when(records.findRecordInScope(7L,1L,"manager")).thenReturn(Optional.of(record));
        assertThrows(BusinessException.class,()->service.update(7L,new UpdateConformityRequest(6,null),principal));
        verify(records,never()).saveAndFlush(any());
    }
}
