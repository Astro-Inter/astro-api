package com.astro.api.form.mapper;

import com.astro.api.form.dto.response.FormSummaryResponse;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.form.dto.response.FormDetailsResponse.QuestionResponse;
import com.astro.api.form.dto.response.FormDetailsResponse.OptionResponse;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormQuestionType;
import com.astro.api.form.dto.request.*;
import com.astro.api.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class FormMapper {
    public static final java.time.ZoneId FORM_ZONE = java.time.ZoneId.of("America/Sao_Paulo");

    public void applyContent(FormWriteRequest body, Form form) {
        var previous = form.getQuestions() == null ? java.util.List.<Form.FormQuestion>of() : form.getQuestions();
        form.setQuestions(toQuestions(body.questions() == null ? java.util.List.of() : body.questions(), previous));
        form.setTitle(clean(body.title()));
        form.setDescription(clean(body.description()));
        form.setManagerId(body.managerId());
        form.setNr(body.nr());
        form.setUnitId(body.unitId());
        form.setDueDate(body.dueDate() == null ? null : body.dueDate().atStartOfDay(FORM_ZONE).toInstant());
    }

    private java.util.List<Form.FormQuestion> toQuestions(java.util.List<FormQuestionRequest> requests,
                                                          java.util.List<Form.FormQuestion> previous) {
        var existing = previous.stream().collect(java.util.stream.Collectors.toMap(Form.FormQuestion::getId, value -> value));
        long nextId = previous.stream().mapToLong(Form.FormQuestion::getId).max().orElse(0) + 1;
        var result = new java.util.ArrayList<Form.FormQuestion>();
        for (var request : requests) {
            if (request.id() != null && !existing.containsKey(request.id())) {
                throw new BusinessException("Identificador de pergunta desconhecido; omita o ID de novas perguntas");
            }
            var question = new Form.FormQuestion();
            question.setId(request.id() == null ? nextId++ : request.id());
            question.setType(request.type().value());
            question.setQuestion(clean(request.question()));
            question.setRequired(Boolean.TRUE.equals(request.required()));
            question.setMultiple(request.type() == FormQuestionType.ALTERNATIVA ? Boolean.TRUE.equals(request.multiple()) : null);
            question.setMaxFiles(request.type() == FormQuestionType.FOTO ? (request.maxFiles() == null ? 1 : request.maxFiles()) : null);
            question.setMaxFileSizeMb(request.type() == FormQuestionType.FOTO ? (request.maxFileSizeMb() == null ? 10 : request.maxFileSizeMb()) : null);
            var old = request.id() == null ? null : existing.get(request.id());
            question.setOptions(toOptions(request.options() == null ? java.util.List.of() : request.options(), old));
            question.setAnswer(old == null || old.getAnswer() == null ? java.util.List.of() : java.util.List.copyOf(old.getAnswer()));
            result.add(question);
        }
        return result;
    }

    private java.util.List<Form.FormOption> toOptions(java.util.List<FormOptionRequest> requests, Form.FormQuestion previous) {
        var oldOptions = previous == null || previous.getOptions() == null ? java.util.List.<Form.FormOption>of() : previous.getOptions();
        var ids = oldOptions.stream().map(Form.FormOption::getId).collect(java.util.stream.Collectors.toSet());
        long nextId = oldOptions.stream().mapToLong(Form.FormOption::getId).max().orElse(0) + 1;
        var result = new java.util.ArrayList<Form.FormOption>();
        for (var request : requests) {
            if (request.id() != null && !ids.contains(request.id())) {
                throw new BusinessException("Identificador de opção desconhecido; omita o ID de novas opções");
            }
            var option = new Form.FormOption();
            option.setId(request.id() == null ? nextId++ : request.id());
            option.setText(Boolean.TRUE.equals(request.other()) ? "Outros" : clean(request.text()));
            option.setOther(Boolean.TRUE.equals(request.other()));
            result.add(option);
        }
        return result;
    }

    private String clean(String value) { return value == null ? "" : value.strip(); }

    public FormSummaryResponse toSummary(Form form) {
        return new FormSummaryResponse(form.getId(), form.getTitle(), form.getDescription(),
                form.getQuestions() == null ? 0 : form.getQuestions().size(), form.getStatus());
    }

    public FormDetailsResponse toDetails(Form form) {
        return new FormDetailsResponse(form.getId(), form.getWorkspaceId(), form.getSenderManagerId(), form.getManagerId(),
                form.getTitle(), form.getDescription(), form.getNr(), form.getUnitId(),
                form.getDueDate() == null ? null : form.getDueDate().atZone(FORM_ZONE).toLocalDate(),
                form.getStatus(), form.getCreatedAt(), form.getUpdatedAt(), form.getRespondedAt(),
                form.getQuestions() == null ? java.util.List.of() : form.getQuestions().stream().map(this::toQuestion).toList());
    }

    private QuestionResponse toQuestion(Form.FormQuestion question) {
        return new QuestionResponse(question.getId(), question.getType(), question.getQuestion(),
                Boolean.TRUE.equals(question.getRequired()), question.getMultiple(),
                question.getOptions() == null ? java.util.List.of() : question.getOptions().stream()
                        .map(option -> new OptionResponse(option.getId(), option.getText(), Boolean.TRUE.equals(option.getOther()))).toList(),
                question.getMaxFiles(), question.getMaxFileSizeMb(),
                question.getAnswer() == null ? java.util.List.of() : java.util.List.copyOf(question.getAnswer()));
    }
}
