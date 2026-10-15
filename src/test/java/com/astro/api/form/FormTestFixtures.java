package com.astro.api.form;

import com.astro.api.form.dto.request.*;
import com.astro.api.form.model.*;
import com.astro.api.form.service.FormValidationService;
import com.astro.api.form.strategy.*;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.workspace.model.Workspace;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

final class FormTestFixtures {
    private FormTestFixtures() {}

    static User manager(long id, long workspaceId, UserType type) {
        Workspace workspace = new Workspace(); workspace.id = workspaceId;
        Unit unit = new Unit(); unit.id = 3L; unit.workspace = workspace; unit.isActive = true;
        User user = new User(); user.setId(id); user.setType(type); user.setStatus(UserStatus.ATIVO); user.setUnit(unit);
        return user;
    }

    static String formId(long value) { return new java.util.UUID(0, value).toString(); }

    static Form form(long id, long workspace, long sender, long recipient, FormStatus status) {
        Form form = new Form(); form.setId(formId(id)); form.setWorkspaceId(workspace); form.setSenderManagerId(sender);
        form.setManagerId(recipient); form.setStatus(status); form.setTitle("Clima");
        form.setCreatedAt(Instant.parse("2026-10-15T12:00:00Z"));
        Form.FormQuestion question = new Form.FormQuestion(); question.setId(1L); question.setAnswer(List.of());
        form.setQuestions(List.of(question));
        return form;
    }

    static FormValidationService validator() {
        return new FormValidationService(List.of(new TextQuestionStrategy(), new OptionQuestionStrategy(),
                new PhotoQuestionStrategy(), new DateQuestionStrategy()));
    }

    static FormQuestionRequest question(FormQuestionType type) {
        return new FormQuestionRequest(null, type, " Pergunta ", true,
                type == FormQuestionType.OPTION ? true : null,
                type == FormQuestionType.OPTION ? List.of(new FormOptionRequest(null, "Sim", false),
                        new FormOptionRequest(null, "", true)) : null, null, null);
    }

    static FormWriteRequest content(FormStatus status) {
        return new FormWriteRequest(" Inspeção ", " Descrição ", 20L, null, null,
                LocalDate.of(2026, 10, 30), status,
                List.of(question(FormQuestionType.TEXT), question(FormQuestionType.OPTION),
                        question(FormQuestionType.PHOTO), question(FormQuestionType.DATE)));
    }
}
