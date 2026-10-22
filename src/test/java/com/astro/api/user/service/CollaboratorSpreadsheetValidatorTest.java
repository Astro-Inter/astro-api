package com.astro.api.user.service;

import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.CollaboratorImportData;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.WorkModel;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollaboratorSpreadsheetValidatorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final CollaboratorSpreadsheetValidator validator = new CollaboratorSpreadsheetValidator(FACTORY.getValidator());
    private final CollaboratorMapper mapper = new CollaboratorMapper();

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void normalizesValidDataAndConvertsAccentedWorkModelsInBothFlows() {
        CollaboratorImportRow row = row(" Ana ", "529.982.247-25", " ANA@astro.com ", " Híbrido ");
        CollaboratorImportData registration = mapper.toRegistrationData(row);
        CollaboratorImportData synchronization = mapper.toImportData(row);

        assertEquals(registration, synchronization);
        assertEquals("Ana", synchronization.name());
        assertEquals("52998224725", synchronization.cpf());
        assertEquals("ana@astro.com", synchronization.email());
        assertEquals(WorkModel.HIBRIDO, synchronization.workModel());
        assertTrue(validator.validateForRegistration(registration, 3).isEmpty());
        assertTrue(validator.validate(synchronization, 3).isEmpty());
    }

    @Test
    void preservesTheRegistrationsSingleCharacterNameAndStricterEmailPattern() {
        CollaboratorImportData shortName = mapper.toImportData(row("A", "52998224725", "ana@astro.com", "Remoto"));
        assertTrue(validator.validateForRegistration(shortName, 3).isEmpty());
        assertEquals("nome", validator.validate(shortName, 3).getFirst().field());
        assertEquals("O nome do colaborador deve ter entre 2 e 255 caracteres",
                validator.validate(shortName, 3).getFirst().message());

        CollaboratorImportData localEmail = mapper.toImportData(row("Ana", "52998224725", "ana@localhost", "Remoto"));
        assertEquals("E-mail inválido", validator.validateForRegistration(localEmail, 3).orElseThrow().message());
        assertTrue(validator.validate(localEmail, 3).isEmpty());
    }

    @Test
    void preservesLegacyCpfCleanupOnlyForRegistration() {
        CollaboratorImportRow row = row("Ana", "CPF: 529.982.247-25", "ana@astro.com", "Remoto");

        assertTrue(validator.validateForRegistration(mapper.toRegistrationData(row), 3).isEmpty());
        List<SpreadsheetImportMessage> errors = validator.validate(mapper.toImportData(row), 3);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().allMatch(error -> error.field().equals("cpf") && error.row() == 3));
    }

    @Test
    void rejectsInvalidCheckDigitsInBothFlowsAndKeepsTheRegistrationErrorContract() {
        CollaboratorImportData invalidCpf = mapper.toImportData(row("Ana", "52998224724", "ana@astro.com", "Remoto"));

        assertEquals(new SpreadsheetImportMessage(3, "cpf", "CPF inválido"),
                validator.validateForRegistration(invalidCpf, 3).orElseThrow());
        assertTrue(validator.validate(invalidCpf, 3).stream().anyMatch(error -> error.field().equals("cpf")));
        assertTrue(validator.validate(invalidCpf, 3).stream()
                .allMatch(error -> error.message().equals("O CPF deve ser válido e conter 11 dígitos")));
        CollaboratorImportData invalidFields = mapper.toImportData(row(" ", "11111111111", "invalido", "Remoto"));
        assertEquals(new SpreadsheetImportMessage(3, "nome", "Nome é obrigatório"),
                validator.validateForRegistration(invalidFields, 3).orElseThrow());
    }

    @Test
    void keepsInvalidWorkModelsNullForEachFlowToReportTheError() {
        CollaboratorImportData data = mapper.toImportData(row("Ana", "52998224725", "ana@astro.com", "invalida"));

        assertNull(data.workModel());
        assertEquals("modalidade", validator.validate(data, 3).getFirst().field());
        assertEquals("A modalidade é obrigatória", validator.validate(data, 3).getFirst().message());
    }

    private CollaboratorImportRow row(String name, String cpf, String email, String workModel) {
        return new CollaboratorImportRow(3, name, cpf, email, "Matriz", "Dev", workModel);
    }
}
