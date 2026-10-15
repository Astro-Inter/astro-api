package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.model.FormQuestionType;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

@Component
public class OptionQuestionStrategy implements FormQuestionStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.ALTERNATIVA; }
    public void validate(FormQuestionRequest question, FormValidationMode mode) {
        QuestionRules.noFileLimits(question);
        var options = question.options() == null ? List.<com.astro.api.form.dto.request.FormOptionRequest>of() : question.options();
        QuestionRules.require(options.size() <= 100 && (mode == FormValidationMode.DRAFT || options.size() >= 2),
                "Perguntas de alternativa devem ter de 2 a 100 opções para publicação");
        int otherCount = 0;
        var ids = new HashSet<Long>();
        for (var option : options) {
            QuestionRules.require(option != null, "Opção inválida");
            QuestionRules.require(option.id() == null || (option.id() > 0 && ids.add(option.id())),
                    "Os identificadores das opções devem ser positivos e únicos na pergunta");
            boolean other = Boolean.TRUE.equals(option.other());
            if (other) otherCount++;
            QuestionRules.require(option.text() == null || option.text().length() <= 80, "Opção deve ter até 80 caracteres");
            QuestionRules.require(mode == FormValidationMode.DRAFT || other || (option.text() != null && !option.text().isBlank()),
                    "Preencha o texto das opções antes da publicação");
        }
        QuestionRules.require(otherCount <= 1, "Cada pergunta aceita somente uma opção Outros");
    }
}
