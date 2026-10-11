package com.astro.api.cargo.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
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
        service = new CargoService(mock(CargoRepository.class), repository, documents);
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

    private NrDocument nr(int id, String description) {
        NrDocument document = new NrDocument();
        document.setId(id);
        document.setDescription(description);
        return document;
    }
}
