package com.astro.api.form.service;

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
    private final Map<FormQuestionType, FormQuestionStrategy> strategies = new EnumMap<>(FormQuestionType.class);

    public FormValidationService(List<FormQuestionStrategy> implementations) {
        for (var strategy : implementations) {
            if (strategies.putIfAbsent(strategy.supportedType(), strategy) != null) {
                throw new IllegalStateException("Estratégia duplicada para " + strategy.supportedType());
            }
        }
        if (strategies.size() != FormQuestionType.values().length) {
            throw new IllegalStateException("Todos os tipos de pergunta precisam de uma estratégia");
        }
    }

    public void validate(FormWriteRequest body, FormValidationMode mode) {
        require(body != null, "Informe os dados do formulário");
        require(body.title() == null || body.title().length() <= 100, "Título deve ter até 100 caracteres");
        require(body.description() == null || body.description().length() <= 240, "Descrição deve ter até 240 caracteres");
        require(body.managerId() == null || body.managerId() > 0, "Gestor deve ter identificador positivo");
        require(body.unitId() == null || body.unitId() > 0, "Unidade deve ter identificador positivo");
        require(body.nrCode() == null || body.nrCode() > 0, "NR deve ter código positivo");
        var questions = body.questions() == null ? List.<FormQuestionRequest>of() : body.questions();
        require(questions.size() <= 100, "O formulário aceita até 100 perguntas");
        if (mode == FormValidationMode.PUBLISH) {
            require(body.title() != null && !body.title().isBlank(), "Informe o título do formulário");
            require(body.managerId() != null, "Selecione o gestor destinatário");
            require(!questions.isEmpty(), "Adicione pelo menos uma pergunta");
        }
        var ids = new HashSet<Long>();
        for (var question : questions) {
            require(question != null && question.type() != null, "Informe um tipo de pergunta válido");
            require(question.id() == null || (question.id() > 0 && ids.add(question.id())),
                    "Os identificadores das perguntas devem ser positivos e únicos");
            require(question.question() == null || question.question().length() <= 140, "Pergunta deve ter até 140 caracteres");
            require(mode == FormValidationMode.DRAFT || (question.question() != null && !question.question().isBlank()),
                    "Preencha o enunciado de todas as perguntas antes da publicação");
            strategies.get(question.type()).validate(question, mode);
        }
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(message);
    }

    public void validateIdentifiers(FormWriteRequest request, List<Form.FormQuestion> existingQuestions) {
        Map<Long, Form.FormQuestion> questionsById = existingQuestions.stream()
                .collect(Collectors.toMap(Form.FormQuestion::getId, question -> question));
        for (var question : request.questions() == null ? List.<FormQuestionRequest>of() : request.questions()) {
            require(question.id() == null || questionsById.containsKey(question.id()),
                    "Identificador de pergunta desconhecido; omita o ID de novas perguntas");
            var previousQuestion = question.id() == null ? null : questionsById.get(question.id());
            var optionIds = previousQuestion == null || previousQuestion.getOptions() == null ? Set.<Long>of()
                    : previousQuestion.getOptions().stream().map(Form.FormOption::getId).collect(Collectors.toSet());
            if (question.options() != null) {
                for (var option : question.options()) {
                    require(option.id() == null || optionIds.contains(option.id()),
                            "Identificador de opção desconhecido; omita o ID de novas opções");
                }
            }
        }
    }
}
