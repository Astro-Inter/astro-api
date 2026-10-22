package com.astro.api.user.service;

import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.user.model.CollaboratorImportData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CollaboratorSpreadsheetValidator {
    private static final List<String> REGISTRATION_FIELD_ORDER = List.of("name", "email", "cpf");
    private final Validator validator;

    public CollaboratorSpreadsheetValidator(Validator validator) {
        this.validator = validator;
    }

    public List<SpreadsheetImportMessage> validate(CollaboratorImportData data, int rowNumber) {
        return validator.validate(data).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> new SpreadsheetImportMessage(rowNumber,
                        spreadsheetField(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
    }

    public Optional<SpreadsheetImportMessage> validateForRegistration(CollaboratorImportData data, int rowNumber) {
        return validator.validate(data, CollaboratorImportData.Registration.class).stream()
                .min(Comparator.comparingInt(violation ->
                        REGISTRATION_FIELD_ORDER.indexOf(violation.getPropertyPath().toString())))
                .map(violation -> registrationError(rowNumber, violation));
    }

    private SpreadsheetImportMessage registrationError(int rowNumber, ConstraintViolation<CollaboratorImportData> violation) {
        String field = violation.getPropertyPath().toString();
        String message = switch (field) {
            case "name" -> "Nome é obrigatório";
            case "email" -> "E-mail inválido";
            case "cpf" -> "CPF inválido";
            default -> throw new IllegalArgumentException("Campo pessoal desconhecido: " + field);
        };
        return new SpreadsheetImportMessage(rowNumber, spreadsheetField(field), message);
    }

    private String spreadsheetField(String field) {
        return switch (field) {
            case "name" -> "nome";
            case "workModel" -> "modalidade";
            default -> field;
        };
    }
}
