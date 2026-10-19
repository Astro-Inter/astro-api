package com.astro.api.workspace.service;

import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.service.CollaboratorSpreadsheetValidator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.CollaboratorSpreadsheetReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class SpreadsheetImportServiceTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void keepsTheWorkspaceImportLimitAtFiveMegabytes() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(5L*1024*1024 + 1);
        SpreadsheetImportService service = new SpreadsheetImportService(mock(UserRepository.class), new CollaboratorSpreadsheetReader(),
                new CollaboratorMapper(), new CollaboratorSpreadsheetValidator(FACTORY.getValidator()));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class,
                () -> service.read(file));
        verify(file, never()).getInputStream();
    }
    @Test
    void keepsInvalidDuplicateRowsOutOfTheBatchAndUsesOnlyBatchLookups() {
        UserRepository userRepository = mock(UserRepository.class);
        SpreadsheetImportService service = new SpreadsheetImportService(userRepository, new CollaboratorSpreadsheetReader(),
                new CollaboratorMapper(), new CollaboratorSpreadsheetValidator(FACTORY.getValidator()));
        when(userRepository.findByEmailInIgnoreCase(anyCollection())).thenReturn(List.of());
        when(userRepository.findByCpfIn(anyCollection())).thenReturn(List.of());

        List<CollaboratorImportRow> rows = List.of(
                new CollaboratorImportRow(2, "Gestora", "11144477735", "gestora@astro.com", "Matriz", "Dev", "REMOTO"),
                new CollaboratorImportRow(3, "Ana", "529.982.247-25", "ana@astro.com", "Matriz", "Dev", "REMOTO"),
                new CollaboratorImportRow(4, "Ana 2", "52998224725", "ana@astro.com", "Matriz", "Dev", "REMOTO")
        );
        Unit unit = new Unit(); unit.name = "Matriz";
        Role role = new Role(); role.setActive(true);

        SpreadsheetImportService.ImportProcessingResult result = service.validateAndBuild(
                rows, List.of(unit), rows.getFirst(), Map.of("dev", role));

        assertEquals(1, result.validUsers().size());
        assertEquals(1, result.errors().size());
        verify(userRepository, times(1)).findByEmailInIgnoreCase(anyCollection());
        verify(userRepository, times(1)).findByCpfIn(anyCollection());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void rejectsRegistrationWhenManagerIsNotInSpreadsheet() {
        SpreadsheetImportService service = new SpreadsheetImportService(mock(UserRepository.class), new CollaboratorSpreadsheetReader(),
                new CollaboratorMapper(), new CollaboratorSpreadsheetValidator(FACTORY.getValidator()));

        org.junit.jupiter.api.Assertions.assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.findManagerRow(List.of(
                        new CollaboratorImportRow(2, "Ana", "52998224725", "ana@astro.com", "Matriz", "Dev", "REMOTO")
                ), "gestora@astro.com"));
    }

    @Test
    void usesTheSharedMapperAndPreservesRegistrationSpecificInputRules() {
        UserRepository repository = mock(UserRepository.class);
        SpreadsheetImportService service = new SpreadsheetImportService(repository, new CollaboratorSpreadsheetReader(),
                new CollaboratorMapper(), new CollaboratorSpreadsheetValidator(FACTORY.getValidator()));
        CollaboratorImportRow manager = new CollaboratorImportRow(2, "Gestora", "11144477735", "gestora@astro.com", "Matriz", "Dev", "Remoto");
        CollaboratorImportRow collaborator = new CollaboratorImportRow(3, " A ", "CPF: 529.982.247-25", " ANA@astro.com ", " MÁTRIZ ", " DÉV ", " Híbrido ");
        Unit unit = new Unit(); unit.name = "Matriz";
        Role role = new Role(); role.setActive(true);

        SpreadsheetImportService.ImportProcessingResult result = service.validateAndBuild(
                List.of(manager, collaborator), List.of(unit), manager, Map.of("dev", role));

        assertEquals(List.of(), result.errors());
        User user = result.validUsers().getFirst();
        assertEquals("A", user.getName());
        assertEquals("52998224725", user.getCpf());
        assertEquals("ana@astro.com", user.getEmail());
        assertEquals(WorkModel.HIBRIDO, user.getWorkModel());
        assertEquals(UserStatus.PRE_CADASTRADO, user.getStatus());
        assertEquals(UserType.COLABORADOR, user.getType());
        assertNull(user.getFirebaseUid());
        assertSame(unit, user.getUnit());
        assertSame(role, user.getRole());
    }

    @Test
    void stillRejectsExistingCpfsInsteadOfApplyingSynchronizationRules() {
        UserRepository repository = mock(UserRepository.class);
        User existing = new User(); existing.setCpf("52998224725");
        when(repository.findByCpfIn(anyCollection())).thenReturn(List.of(existing));
        SpreadsheetImportService service = new SpreadsheetImportService(repository, new CollaboratorSpreadsheetReader(),
                new CollaboratorMapper(), new CollaboratorSpreadsheetValidator(FACTORY.getValidator()));
        CollaboratorImportRow manager = new CollaboratorImportRow(2, "Gestora", "11144477735", "gestora@astro.com", "Matriz", "Dev", "Remoto");
        CollaboratorImportRow collaborator = new CollaboratorImportRow(3, "Ana", "52998224725", "ana@astro.com", "Matriz", "Dev", "Remoto");

        SpreadsheetImportService.ImportProcessingResult result = service.validateAndBuild(
                List.of(manager, collaborator), List.of(), manager, Map.of());

        assertEquals(List.of(), result.validUsers());
        assertEquals("CPF já cadastrado", result.errors().getFirst().message());
        verify(repository, never()).save(any());
    }
}
