package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformity.model.*;
import com.astro.api.conformity.repository.*;
import com.astro.api.conformity.service.FormConformityService;
import com.astro.api.form.model.*;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormConformityServiceTest {
    final ConformityRepository writes = mock(ConformityRepository.class);
    final UnitConformityRepository records = mock(UnitConformityRepository.class);
    final NrCatalogRepository nrs = mock(NrCatalogRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final FormConformityService service = new FormConformityService(writes, records, nrs, users,
            new com.astro.api.conformity.mapper.UnitConformityMapper());
    final AuthenticatedUser principal = new AuthenticatedUser("reviewer", "reviewer@astro.local", Role.GESTOR_WORKSPACE);
    Form form() {
        Form form = new Form(); form.setId("form-657"); form.setWorkspaceId(1L); form.setManagerId(20L);
        form.setUnitId(2L); form.setNrCode(1); form.setStatus(FormStatus.COMPLETED);
        form.setRespondedAt(Instant.parse("2024-08-31T02:00:00Z"));
        form.setReview(new FormReview(FormReviewStatus.APPROVED, 1L, Instant.now(), null));
        return form;
    }
    Unit unit(long id) {
        Unit unit = new Unit(); unit.id = id; unit.workspace = new Workspace(); unit.workspace.id = 1L;
        when(records.findUnitInScope(id, 1L, "reviewer")).thenReturn(Optional.of(unit));
        NrCatalog nr = mock(NrCatalog.class); when(nr.getRefresherFrequency()).thenReturn(6);
        when(nrs.findLockedByCodeNr(1)).thenReturn(Optional.of(nr)); return unit;
    }
    @Test void explicitUnitWinsAndUsesResponseDateWithCalendarMonthsAndSaoPauloTimezone() {
        Form form = form(); unit(2L); service.synchronizeApproved(form, principal);
        ArgumentCaptor<Conformity> captured = ArgumentCaptor.forClass(Conformity.class);
        verify(records).saveAndFlush(captured.capture()); Conformity value = captured.getValue();
        assertEquals("UNIDADE", value.getType()); assertEquals(2L, value.getUnitId()); assertNull(value.getCollaboratorId());
        assertEquals(LocalDate.of(2025,2,28), value.getExpiresAt()); assertEquals("FORMULARIO", value.getOrigin());
        assertEquals(form.getId(), value.getFormResponseId()); verifyNoInteractions(users);
    }
    @Test void withoutUnitUsesRecipientRatherThanReviewerAndKeepsWorkspaceScope() {
        Form form = form(); form.setUnitId(null); Unit unit = unit(5L);
        User recipient = new User(); recipient.setUnit(unit); when(users.findById(20L)).thenReturn(Optional.of(recipient));
        service.synchronizeApproved(form, principal);
        verify(records).findUnitInScope(5L,1L,"reviewer");
        verify(records).saveAndFlush(argThat(value -> value.getUnitId().equals(5L)));
    }
    @Test void repeatedApprovalNeverCreatesDuplicateOrReactivatesHistoricalRecord() {
        when(records.existsByFormResponseId("form-657")).thenReturn(true);
        service.synchronizeApproved(form(), principal); verify(records,never()).saveAndFlush(any()); verifyNoInteractions(users,nrs);
    }
    @Test void rejectedOrUnlinkedFormDoesNotWrite() {
        Form form = form(); form.setReview(new FormReview(FormReviewStatus.REJECTED,1L,Instant.now(),"Reprovado"));
        service.synchronizeApproved(form,principal); form.setReview(new FormReview(FormReviewStatus.APPROVED,1L,Instant.now(),null));
        form.setNrCode(null); service.synchronizeApproved(form,principal); verifyNoInteractions(writes,records,users,nrs);
    }
    @Test void unauthorizedTargetNeverGeneratesConformity() {
        assertThrows(ResourceNotFoundException.class, () -> service.synchronizeApproved(form(), principal));
        verify(records,never()).saveAndFlush(any());
    }
}
