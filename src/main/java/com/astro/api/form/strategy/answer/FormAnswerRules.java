package com.astro.api.form.strategy.answer;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormQuestionType;
import java.util.Collection;

final class FormAnswerRules {
    private FormAnswerRules() {
    }

    static boolean present(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    static String clean(String text) {
        return text == null ? "" : text.strip();
    }

    static void requireAnswer(Form.FormQuestion question, boolean answered) {
        if (Boolean.TRUE.equals(question.getRequired()) && !answered) {
            throw new BusinessException("Responda a pergunta obrigatória " + question.getId());
        }
    }

    static void requireType(FormQuestionAnswerRequest answer, FormQuestionType type) {
        boolean incompatible = (type != FormQuestionType.TEXT && answer.text() != null)
                || (type != FormQuestionType.OPTION && (answer.optionIds() != null || answer.otherText() != null))
                || (type != FormQuestionType.DATE && answer.date() != null)
                || (type != FormQuestionType.PHOTO && answer.fileIndexes() != null);
        if (incompatible) throw new BusinessException("A resposta contém campos incompatíveis com o tipo da pergunta");
    }
}
