package com.astro.api.form.strategy;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.FormQuestionDefinitionRequest;

final class QuestionRules {
    private QuestionRules() {
    }

    static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(message);
    }

    static void noAlternatives(FormQuestionDefinitionRequest question) {
        require((question.options() == null || question.options().isEmpty()) && !Boolean.TRUE.equals(question.multiple()),
                "Opções e múltipla escolha só são permitidas em perguntas de alternativa");
    }

    static void noFileLimits(FormQuestionDefinitionRequest question) {
        require(question.maxFiles() == null && question.maxFileSizeMb() == null,
                "Limites de arquivos só são permitidos em perguntas de foto");
    }
}
