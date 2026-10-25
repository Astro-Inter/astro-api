package com.astro.api.form.validation;

import com.astro.api.common.exception.BusinessException;
import java.util.UUID;

public final class FormIdentifierValidator {
    private FormIdentifierValidator() {
    }

    public static String normalize(String formId) {
        if (formId == null || formId.length() != 36) {
            throw new BusinessException("O identificador do formulário deve ser um UUID válido");
        }
        try {
            return UUID.fromString(formId).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("O identificador do formulário deve ser um UUID válido");
        }
    }
}
