package com.astro.api.form.service;

import com.astro.api.form.dto.request.FormOptionRequest;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.FormWriteRequest;
import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormQuestionType;
import com.astro.api.form.strategy.*;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FormValidationService {
    private final Map<FormQuestionType, FormQuestionStrategy> questionStrategiesByType = new EnumMap<>(FormQuestionType.class);

    public FormValidationService(List<FormQuestionStrategy> questionStrategies) {
        for (FormQuestionStrategy questionStrategy : questionStrategies) {
            if (questionStrategiesByType.putIfAbsent(questionStrategy.supportedType(), questionStrategy) != null) {
                throw new IllegalStateException("Estratégia duplicada para " + questionStrategy.supportedType());
            }
        }
        if (questionStrategiesByType.size() != FormQuestionType.values().length) {
            throw new IllegalStateException("Todos os tipos de pergunta precisam de uma estratégia");
        }
    }

    public void validate(FormWriteRequest formRequest, FormValidationMode mode) {
        require(formRequest != null, "Informe os dados do formulário");
        require(formRequest.title() == null || formRequest.title().length() <= 100, "Título deve ter até 100 caracteres");
        require(formRequest.description() == null || formRequest.description().length() <= 240, "Descrição deve ter até 240 caracteres");
        require(formRequest.managerId() == null || formRequest.managerId() > 0, "Gestor deve ter identificador positivo");
        require(formRequest.unitId() == null || formRequest.unitId() > 0, "Unidade deve ter identificador positivo");
        require(formRequest.nrCode() == null || formRequest.nrCode() > 0, "NR deve ter código positivo");
        List<FormQuestionRequest> questions = formRequest.questions() == null ? List.<FormQuestionRequest>of() : formRequest.questions();
        require(questions.size() <= 100, "O formulário aceita até 100 perguntas");
        if (mode == FormValidationMode.PUBLISH) {
            require(formRequest.title() != null && !formRequest.title().isBlank(), "Informe o título do formulário");
            require(formRequest.managerId() != null, "Selecione o gestor destinatário");
            require(!questions.isEmpty(), "Adicione pelo menos uma pergunta");
        }
        Set<Long> ids = new HashSet<Long>();
        for (FormQuestionRequest question : questions) {
            require(question != null && question.type() != null, "Informe um tipo de pergunta válido");
            require(question.id() == null || (question.id() > 0 && ids.add(question.id())),
                    "Os identificadores das perguntas devem ser positivos e únicos");
            require(question.question() == null || question.question().length() <= 140, "Pergunta deve ter até 140 caracteres");
            require(mode == FormValidationMode.DRAFT || (question.question() != null && !question.question().isBlank()),
                    "Preencha o enunciado de todas as perguntas antes da publicação");
            questionStrategiesByType.get(question.type()).validate(question, mode);
        }
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(message);
    }

    public void validateIdentifiers(FormWriteRequest formRequest, List<Form.FormQuestion> existingQuestions) {
        Map<Long, Form.FormQuestion> questionsById = existingQuestions.stream()
                .collect(Collectors.toMap(Form.FormQuestion::getId, question -> question));
        for (FormQuestionRequest question : formRequest.questions() == null ? List.<FormQuestionRequest>of() : formRequest.questions()) {
            require(question.id() == null || questionsById.containsKey(question.id()),
                    "Identificador de pergunta desconhecido; omita o ID de novas perguntas");
            Form.FormQuestion previousQuestion = question.id() == null ? null : questionsById.get(question.id());
            Set<Long> optionIds = previousQuestion == null || previousQuestion.getOptions() == null ? Set.<Long>of()
                    : previousQuestion.getOptions().stream().map(Form.FormOption::getId).collect(Collectors.toSet());
            if (question.options() != null) {
                for (FormOptionRequest option : question.options()) {
                    require(option.id() == null || optionIds.contains(option.id()),
                            "Identificador de opção desconhecido; omita o ID de novas opções");
                }
            }
        }
    }
}
