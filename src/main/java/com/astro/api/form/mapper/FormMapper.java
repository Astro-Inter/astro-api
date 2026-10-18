package com.astro.api.form.mapper;

import java.util.Map;
import com.astro.api.form.dto.response.FormRecipientsResponse;
import com.astro.api.user.repository.FormRecipientProjection;
import com.astro.api.form.dto.response.FormSummaryResponse;
import com.astro.api.form.dto.response.FormPageResponse;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.form.dto.response.FormDetailsResponse.QuestionResponse;
import com.astro.api.form.dto.response.FormDetailsResponse.OptionResponse;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormQuestionType;
import com.astro.api.form.dto.request.*;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FormMapper {
    public static final ZoneId FORM_ZONE = ZoneId.of("America/Sao_Paulo");

    public void applyContent(FormRequest formRequest, Form form) {
        List<Form.FormQuestion> previous = form.getQuestions() == null ? List.<Form.FormQuestion>of() : form.getQuestions();
        form.setQuestions(toQuestions(formRequest.questions() == null ? List.of() : formRequest.questions(), previous));
        form.setTitle(clean(formRequest.title()));
        form.setDescription(clean(formRequest.description()));
        form.setManagerId(formRequest.managerId());
        form.setNrCode(formRequest.nrCode());
        form.setUnitId(formRequest.unitId());
        form.setDueDate(formRequest.dueDate() == null ? null : formRequest.dueDate().atStartOfDay(FORM_ZONE).toInstant());
    }

    private List<Form.FormQuestion> toQuestions(List<FormQuestionDefinitionRequest> requests,
                                                List<Form.FormQuestion> previous) {
        Map<Long, Form.FormQuestion> existing = previous.stream().collect(Collectors.toMap(Form.FormQuestion::getId, value -> value));
        long nextId = previous.stream().mapToLong(Form.FormQuestion::getId).max().orElse(0) + 1;
        List<Form.FormQuestion> result = new ArrayList<>();
        for (FormQuestionDefinitionRequest request : requests) {
            Form.FormQuestion question = new Form.FormQuestion();
            question.setId(request.id() == null ? nextId++ : request.id());
            question.setType(request.type().value());
            question.setQuestion(clean(request.question()));
            question.setRequired(Boolean.TRUE.equals(request.required()));
            question.setMultiple(request.type() == FormQuestionType.OPTION ? Boolean.TRUE.equals(request.multiple()) : null);
            question.setMaxFiles(request.type() == FormQuestionType.PHOTO ? (request.maxFiles() == null ? 1 : request.maxFiles()) : null);
            question.setMaxFileSizeMb(request.type() == FormQuestionType.PHOTO ? (request.maxFileSizeMb() == null ? 10 : request.maxFileSizeMb()) : null);
            Form.FormQuestion old = request.id() == null ? null : existing.get(request.id());
            question.setOptions(toOptions(request.options() == null ? List.of() : request.options(), old));
            question.setAnswer(old == null || old.getAnswer() == null ? List.of() : List.copyOf(old.getAnswer()));
            result.add(question);
        }
        return result;
    }

    private List<Form.FormOption> toOptions(List<FormOptionRequest> requests, Form.FormQuestion previous) {
        List<Form.FormOption> oldOptions = previous == null || previous.getOptions() == null ? List.<Form.FormOption>of() : previous.getOptions();
        long nextId = oldOptions.stream().mapToLong(Form.FormOption::getId).max().orElse(0) + 1;
        List<Form.FormOption> result = new ArrayList<>();
        for (FormOptionRequest request : requests) {
            Form.FormOption option = new Form.FormOption();
            option.setId(request.id() == null ? nextId++ : request.id());
            option.setText(Boolean.TRUE.equals(request.other()) ? "Outros" : clean(request.text()));
            option.setOther(Boolean.TRUE.equals(request.other()));
            result.add(option);
        }
        return result;
    }

    private String clean(String value) { return value == null ? "" : value.strip(); }

    public FormRequest toFormRequest(Form form) {
        List<FormQuestionDefinitionRequest> questions = form.getQuestions() == null ? List.<FormQuestionDefinitionRequest>of() : form.getQuestions().stream()
                .map(question -> new FormQuestionDefinitionRequest(question.getId(), FormQuestionType.fromValue(question.getType()), question.getQuestion(),
                        question.getRequired(), question.getMultiple(), question.getOptions() == null ? List.of()
                        : question.getOptions().stream().map(option -> new FormOptionRequest(option.getId(), option.getText(), option.getOther())).toList(),
                        question.getMaxFiles(), question.getMaxFileSizeMb())).toList();
        return new FormRequest(form.getTitle(), form.getDescription(), form.getManagerId(), form.getNrCode(), form.getUnitId(),
                form.getDueDate() == null ? null : form.getDueDate().atZone(FORM_ZONE).toLocalDate(), form.getStatus(), questions);
    }

    public FormRecipientsResponse toRecipients(Long currentManagerId, List<FormRecipientProjection> recipients) {
        return new FormRecipientsResponse(currentManagerId, recipients.stream()
                .map(recipient -> new FormRecipientsResponse.RecipientResponse(
                        recipient.getId(), recipient.getName(), recipient.getUnitName()))
                .toList());
    }

    public FormSummaryResponse toSummary(Form form) {
        return new FormSummaryResponse(form.getId(), form.getTitle(), form.getDescription(),
                form.getQuestions() == null ? 0 : form.getQuestions().size(), form.getStatus());
    }

    public FormPageResponse toPage(List<Form> forms, int page, int size, long total) {
        return new FormPageResponse(forms.stream().map(this::toSummary).toList(), page, size,
                total, (total + size - 1) / size);
    }

    public FormDetailsResponse toDetails(Form form) {
        return new FormDetailsResponse(form.getId(), form.getWorkspaceId(), form.getSenderManagerId(), form.getManagerId(),
                form.getTitle(), form.getDescription(), form.getNrCode(), form.getUnitId(),
                form.getDueDate() == null ? null : form.getDueDate().atZone(FORM_ZONE).toLocalDate(),
                form.getStatus(), form.getCreatedAt(), form.getUpdatedAt(), form.getRespondedAt(),
                form.getQuestions() == null ? List.of() : form.getQuestions().stream().map(this::toQuestion).toList());
    }

    private QuestionResponse toQuestion(Form.FormQuestion question) {
        return new QuestionResponse(question.getId(), question.getType(), question.getQuestion(),
                Boolean.TRUE.equals(question.getRequired()), question.getMultiple(),
                question.getOptions() == null ? List.of() : question.getOptions().stream()
                        .map(option -> new OptionResponse(option.getId(), option.getText(), Boolean.TRUE.equals(option.getOther()))).toList(),
                question.getMaxFiles(), question.getMaxFileSizeMb(),
                question.getAnswer() == null ? List.of() : List.copyOf(question.getAnswer()));
    }
}
