package com.astro.api.cargo.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.cargo.dto.request.CargoRequest;
import com.astro.api.cargo.dto.request.CargoNrRequest;
import com.astro.api.cargo.dto.request.UpdateCargoNrsRequest;
import com.astro.api.cargo.dto.response.CargoNrResponse;
import com.astro.api.cargo.repository.CargoManagementRepository;
import com.astro.api.cargo.repository.CargoRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformidade.model.NrDocument;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CargoManagementServiceTest {
    private final CargoManagementRepository repository = mock(CargoManagementRepository.class);
    private final NrDocumentRepository documents = mock(NrDocumentRepository.class);
    private final AuthenticatedUser user = new AuthenticatedUser("gestor", "gestor@astro.com", Role.GESTOR);
    private CargoService service;

    @BeforeEach
    void setUp() {
        service = new CargoService(mock(CargoRepository.class), repository, documents,
                tools.jackson.databind.json.JsonMapper.builder().build());
    }

    @Test
    void rejectsAnotherManagersIdentityBeforeAccessingData() {
        assertThrows(AccessDeniedException.class, () -> service.findAll("outro", user));
        assertThrows(AccessDeniedException.class, () -> service.findNrs("outro", 12L, user));
        assertThrows(AccessDeniedException.class, () -> service.findNrOptions("outro", 12L, user));
        verifyNoInteractions(repository, documents);
    }

    @Test
    void rejectsBlankIdentityAndInvalidCargoId() {
        assertThrows(BusinessException.class, () -> service.findAll(" ", user));
        assertThrows(BusinessException.class, () -> service.findNrs("gestor", 0L, user));
        verifyNoInteractions(repository, documents);
    }

    @Test
    void mergesNumericMongoIdsWithLinkedNrsAndSortsTheOptions() {
        when(repository.findNrs("gestor", 12L)).thenReturn(List.of(new CargoNrResponse(1, "SQL")));
        when(documents.findByUsability("Colaborador")).thenReturn(List.of(nr(17, "Ergonomia"), nr(1, "Mongo")));

        var options = service.findNrOptions("gestor", 12L, user);

        assertEquals(2, options.size());
        assertEquals(1, options.getFirst().nr());
        assertEquals("Mongo", options.getFirst().descricao());
        assertTrue(options.getFirst().ativo());
        assertFalse(options.getLast().ativo());
        verify(documents).findByUsability("Colaborador");
    }

    @Test
    void doesNotReadMongoWhenDatabaseRejectsTheCargo() {
        when(repository.findNrs("gestor", 12L)).thenThrow(new BusinessException("Cargo fora do workspace"));
        assertThrows(BusinessException.class, () -> service.findNrOptions("gestor", 12L, user));
        verifyNoInteractions(documents);
    }

    @Test
    void preservesFalseWhenDeactivatingCargo() {
        when(repository.toggleStatus("gestor", 12L)).thenReturn(false);
        assertFalse(service.toggleStatus("gestor", 12L, user));
    }

    @Test
    void rejectsDuplicateNrsBeforeExecutingTheProcedure() {
        var body = new UpdateCargoNrsRequest("gestor", List.of(
                new CargoNrRequest(1, true), new CargoNrRequest(1, false)));
        assertThrows(BusinessException.class, () -> service.updateNrs(12L, body, user));
        verifyNoInteractions(repository);
    }

    @Test
    void serializesOnlyTheSpecifiedNrChangesAsJsonbPayload() throws Exception {
        var body = new UpdateCargoNrsRequest("gestor", List.of(
                new CargoNrRequest(1, true), new CargoNrRequest(17, false)));
        service.updateNrs(12L, body, user);
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).updateNrs(eq("gestor"), eq(12L), json.capture());
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.getValue());
        assertEquals(2, tree.size());
        assertTrue(tree.get(0).get("ativo").asBoolean());
        assertFalse(tree.get(1).get("ativo").asBoolean());
        assertEquals(17, tree.get(1).get("nr").asInt());
    }

    @Test
    void trimsNameBeforeApplyingDatabaseLengthLimits() {
        service.create(new CargoRequest("gestor", "  Gerente  ", "Ativo"), user);
        verify(repository).create("gestor", "Gerente", "Ativo");
        assertThrows(BusinessException.class,
                () -> service.create(new CargoRequest("gestor", " a ", "Ativo"), user));
        assertThrows(BusinessException.class,
                () -> service.update(12L, new CargoRequest("gestor", "x".repeat(256), "Ativo"), user));
    }

    @Test
    void rejectsImpersonationForEveryMutation() {
        var request = new CargoRequest("outro", "Gerente", "Ativo");
        assertThrows(AccessDeniedException.class, () -> service.create(request, user));
        assertThrows(AccessDeniedException.class, () -> service.update(12L, request, user));
        assertThrows(AccessDeniedException.class, () -> service.toggleStatus("outro", 12L, user));
        assertThrows(AccessDeniedException.class, () -> service.updateNrs(12L,
                new UpdateCargoNrsRequest("outro", List.of()), user));
        verifyNoInteractions(repository);
    }

    private NrDocument nr(int id, String description) {
        NrDocument document = new NrDocument();
        document.setId(id);
        document.setDescription(description);
        return document;
    }
}
