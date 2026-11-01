package com.astro.api.role.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.role.dto.request.RoleRequest;
import com.astro.api.role.dto.request.RoleNrRequest;
import com.astro.api.role.dto.request.UpdateRoleNrsRequest;
import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.repository.RoleManagementRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.model.NrDocument;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.role.mapper.RoleNrMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import com.astro.api.conformity.repository.NrDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleManagementServiceTest {
    private final RoleManagementRepository repository = mock(RoleManagementRepository.class);
    private final NrDocumentRepository documents = mock(NrDocumentRepository.class);
    private final AuthenticatedUser user = new AuthenticatedUser("gestor", "gestor@astro.com", Role.GESTOR);
    private final NrCatalogRepository catalog = mock(NrCatalogRepository.class);
    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(mock(RoleRepository.class), repository, documents, catalog,
                new RoleNrMapper(), tools.jackson.databind.json.JsonMapper.builder().build());
    }

    @Test
    void rejectsAnotherManagersIdentityBeforeAccessingData() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null));
        assertThrows(AccessDeniedException.class, () -> service.findNrs(12L, null));
        assertThrows(AccessDeniedException.class, () -> service.findNrOptions(12L, null));
        verifyNoInteractions(repository, documents, catalog);
    }

    @Test
    void rejectsBlankIdentityAndInvalidRoleId() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(new AuthenticatedUser(" ", "", Role.GESTOR)));
        assertThrows(BusinessException.class, () -> service.findNrs(0L, user));
        verifyNoInteractions(repository, documents, catalog);
    }

    @Test
    void mergesNumericMongoIdsWithLinkedNrsAndSortsTheOptions() {
        when(repository.findNrs("gestor", 12L)).thenReturn(List.of(new RoleNrResponse(1, "SQL")));
        when(documents.findByUsability("Colaborador")).thenReturn(List.of(nr(17, "Ergonomia"), nr(1, "Mongo")));
        when(catalog.findAllById(Set.of(1, 17))).thenReturn(List.of(catalogNr(1), catalogNr(17)));

        var options = service.findNrOptions(12L, user);

        assertEquals(2, options.size());
        assertEquals(1, options.getFirst().nr());
        assertEquals("Mongo", options.getFirst().description());
        assertTrue(options.getFirst().active());
        assertFalse(options.getLast().active());
        verify(documents).findByUsability("Colaborador");
    }

    @Test
    void doesNotReadMongoWhenDatabaseRejectsTheRole() {
        when(repository.findNrs("gestor", 12L)).thenThrow(new BusinessException("Cargo fora do workspace"));
        assertThrows(BusinessException.class, () -> service.findNrOptions(12L, user));
        verifyNoInteractions(documents);
    }

    @Test
    void preservesFalseWhenDeactivatingRole() {
        when(repository.toggleStatus("gestor", 12L)).thenReturn(false);
        assertFalse(service.toggleStatus(12L, user));
    }

    @Test
    void rejectsDuplicateNrsBeforeExecutingTheProcedure() {
        var body = new UpdateRoleNrsRequest(List.of(
                new RoleNrRequest(1, true), new RoleNrRequest(1, false)));
        assertThrows(BusinessException.class, () -> service.updateNrs(12L, body, user));
        verifyNoInteractions(repository);
    }

    @Test
    void serializesOnlyTheSpecifiedNrChangesAsJsonbPayload() throws Exception {
        when(catalog.findAllById(Set.of(1, 17))).thenReturn(List.of(catalogNr(1), catalogNr(17)));
        var body = new UpdateRoleNrsRequest(List.of(
                new RoleNrRequest(1, true), new RoleNrRequest(17, false)));
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
        service.create(new RoleRequest("  Gerente  ", "Ativo"), user);
        verify(repository).create("gestor", "Gerente", "Ativo");
        assertThrows(BusinessException.class,
                () -> service.create(new RoleRequest(" a ", "Ativo"), user));
        assertThrows(BusinessException.class,
                () -> service.update(12L, new RoleRequest("x".repeat(256), "Ativo"), user));
    }

    @Test
    void rejectsMissingAndUnauthorizedPrincipalsForEveryMutation() {
        RoleRequest request = new RoleRequest("Gerente", "Ativo");
        for (AuthenticatedUser unauthorized : java.util.Arrays.asList(null,
                new AuthenticatedUser("outro", "", Role.COLABORADOR),
                new AuthenticatedUser("outro", "", Role.ADMIN),
                new AuthenticatedUser("", "", Role.GESTOR))) {
            assertThrows(AccessDeniedException.class, () -> service.create(request, unauthorized));
            assertThrows(AccessDeniedException.class, () -> service.update(12L, request, unauthorized));
            assertThrows(AccessDeniedException.class, () -> service.toggleStatus(12L, unauthorized));
            assertThrows(AccessDeniedException.class, () -> service.updateNrs(12L,
                    new UpdateRoleNrsRequest(List.of()), unauthorized));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void sendsOnlyTheAuthenticatedUidToEveryDatabaseOperation() {
        service.findAll(user);
        service.findNrs(12L, user);
        service.create(new RoleRequest("Gerente", "Ativo"), user);
        service.update(12L, new RoleRequest("Gerente", "Ativo"), user);
        service.toggleStatus(12L, user);
        service.updateNrs(12L, new UpdateRoleNrsRequest(List.of()), user);
        verify(repository).findAll("gestor");
        verify(repository).findNrs("gestor", 12L);
        verify(repository).create("gestor", "Gerente", "Ativo");
        verify(repository).update("gestor", 12L, "Gerente", "Ativo");
        verify(repository).toggleStatus("gestor", 12L);
        verify(repository).updateNrs("gestor", 12L, "[]");
    }

    @Test
    void offersOnlyMongoOptionsThatExistInTheRelationalCatalog() {
        when(repository.findNrs("gestor", 12L)).thenReturn(List.of(new RoleNrResponse(1, "SQL")));
        when(documents.findByUsability("Colaborador")).thenReturn(List.of(nr(4, "Sem catálogo SQL"), nr(1, "Mongo")));
        when(catalog.findAllById(Set.of(1, 4))).thenReturn(List.of(catalogNr(1)));

        var options = service.findNrOptions(12L, user);

        assertEquals(1, options.size());
        assertEquals(1, options.getFirst().nr());
        assertEquals("Mongo", options.getFirst().description());
        assertTrue(options.getFirst().active());
    }

    @Test
    void keepsLinkedNrsAvailableForRemovalWhenMongoNoLongerOffersThem() {
        when(repository.findNrs("gestor", 12L)).thenReturn(List.of(new RoleNrResponse(17, "Descrição SQL")));
        when(documents.findByUsability("Colaborador")).thenReturn(List.of(nr(1, "Descrição Mongo")));
        when(catalog.findAllById(Set.of(1, 17))).thenReturn(List.of(catalogNr(1), catalogNr(17)));

        var options = service.findNrOptions(12L, user);

        assertEquals(List.of(1, 17), options.stream().map(option -> option.nr()).toList());
        assertFalse(options.getFirst().active());
        assertEquals("Descrição SQL", options.getLast().description());
        assertTrue(options.getLast().active());
    }

    @Test
    void ignoresUnselectedMissingCodesFromAnOlderOptionsList() throws Exception {
        when(catalog.findAllById(Set.of(1, 4))).thenReturn(List.of(catalogNr(1)));
        service.updateNrs(12L, new UpdateRoleNrsRequest(List.of(
                new RoleNrRequest(1, true), new RoleNrRequest(4, false))), user);

        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).updateNrs(eq("gestor"), eq(12L), json.capture());
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.getValue());
        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).get("nr").asInt());
        assertTrue(tree.get(0).get("ativo").asBoolean());
    }

    @Test
    void rejectsAnUnavailableSelectedNrBeforeWritingAnyChanges() {
        when(catalog.findAllById(Set.of(1, 4))).thenReturn(List.of(catalogNr(1)));
        var exception = assertThrows(BusinessException.class, () -> service.updateNrs(12L,
                new UpdateRoleNrsRequest(List.of(
                        new RoleNrRequest(1, true), new RoleNrRequest(4, true))), user));

        assertEquals("NRs indisponíveis no catálogo: 4. Atualize a lista e tente novamente.", exception.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void keepsTheProcedureAuthorizationCheckWhenAllStaleItemsAreUnselected() {
        when(catalog.findAllById(Set.of(4))).thenReturn(List.of());
        service.updateNrs(12L, new UpdateRoleNrsRequest(List.of(new RoleNrRequest(4, false))), user);
        verify(repository).updateNrs("gestor", 12L, "[]");
    }

    @Test
    void doesNotTurnACatalogConnectionFailureIntoAnEmptySuccessfulUpdate() {
        var failure = new DataAccessResourceFailureException("offline");
        when(catalog.findAllById(Set.of(1))).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.updateNrs(12L,
                new UpdateRoleNrsRequest(List.of(new RoleNrRequest(1, true))), user)));
        verifyNoInteractions(repository);
    }

    @Test
    void savesEveryOptionReturnedByTheApiWhenMongoContainsAnUnpersistedNr() throws Exception {
        when(repository.findNrs("gestor", 12L)).thenReturn(List.of());
        when(documents.findByUsability("Colaborador")).thenReturn(List.of(nr(4, "Mongo"), nr(1, "Mongo")));
        when(catalog.findAllById(Set.of(1, 4))).thenReturn(List.of(catalogNr(1)));
        when(catalog.findAllById(Set.of(1))).thenReturn(List.of(catalogNr(1)));

        var options = service.findNrOptions(12L, user);
        service.updateNrs(12L, new UpdateRoleNrsRequest(options.stream()
                .map(option -> new RoleNrRequest(option.nr(), true)).toList()), user);

        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).updateNrs(eq("gestor"), eq(12L), json.capture());
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.getValue());
        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).get("nr").asInt());
    }

    private NrCatalog catalogNr(int id) {
        var nr = new NrCatalog();
        org.springframework.test.util.ReflectionTestUtils.setField(nr, "codeNr", id);
        return nr;
    }

    private NrDocument nr(int id, String description) {
        NrDocument document = new NrDocument();
        document.setId(id);
        document.setDescription(description);
        return document;
    }
}
