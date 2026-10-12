package com.astro.api.workspace.service;

import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class SpreadsheetImportServiceTest {
    @Test
    void keepsInvalidDuplicateRowsOutOfTheBatchAndUsesOnlyBatchLookups() {
        UserRepository userRepository = mock(UserRepository.class);
        SpreadsheetImportService service = new SpreadsheetImportService(userRepository);
        when(userRepository.findByEmailInIgnoreCase(anyCollection())).thenReturn(List.of());
        when(userRepository.findByCpfIn(anyCollection())).thenReturn(List.of());

        List<SpreadsheetImportService.SpreadsheetRow> rows = List.of(
                new SpreadsheetImportService.SpreadsheetRow(2, "Gestora", "11144477735", "gestora@astro.com", "Matriz", "Dev", "REMOTO"),
                new SpreadsheetImportService.SpreadsheetRow(3, "Ana", "529.982.247-25", "ana@astro.com", "Matriz", "Dev", "REMOTO"),
                new SpreadsheetImportService.SpreadsheetRow(4, "Ana 2", "52998224725", "ana@astro.com", "Matriz", "Dev", "REMOTO")
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
        SpreadsheetImportService service = new SpreadsheetImportService(mock(UserRepository.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.findManagerRow(List.of(
                        new SpreadsheetImportService.SpreadsheetRow(2, "Ana", "52998224725", "ana@astro.com", "Matriz", "Dev", "REMOTO")
                ), "gestora@astro.com"));
    }
}
