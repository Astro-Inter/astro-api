package com.astro.api.form.service;

import com.astro.api.form.model.Form;
import com.astro.api.form.repository.FormRepository;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.response.FormPageResponse;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.form.dto.request.FormWriteRequest;
import com.astro.api.form.strategy.FormValidationMode;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.model.FormScope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class FormService {
    private final FormRepository formRepository;
    private final FormAccessService access;
    private final FormMapper mapper;
    private final FormValidationService validation;
    private final FormReferenceService references;

    public FormService(FormRepository formRepository, FormAccessService access, FormMapper mapper,
                       FormValidationService validation, FormReferenceService references) {
        this.formRepository = formRepository;
        this.access = access;
        this.mapper = mapper;
        this.validation = validation;
        this.references = references;
    }

    public FormDetailsResponse create(FormWriteRequest body, AuthenticatedUser principal) {
        var scope = access.requireManager(principal);
        if (body == null || (body.status() != null && body.status() != FormStatus.DRAFT && body.status() != FormStatus.PENDING)) {
            throw new BusinessException("Crie o formulário como RASCUNHO ou PENDENTE");
        }
        FormStatus status = body.status() == null ? FormStatus.PENDING : body.status();
        validation.validate(body, status == FormStatus.DRAFT ? FormValidationMode.DRAFT : FormValidationMode.PUBLISH);
        validation.validateIdentifiers(body, List.of());
        references.validate(body, scope);
        Form form = new Form();
        mapper.applyContent(body, form);
        form.setId(UUID.randomUUID().toString());
        form.setWorkspaceId(scope.workspaceId());
        form.setSenderManagerId(scope.managerId());
        form.setStatus(status);
        form.setCreatedAt(now());
        form.setUpdatedAt(form.getCreatedAt());
        form.setVersion(0L);
        return mapper.toDetails(formRepository.insert(form));
    }

    public FormDetailsResponse update(String id, FormWriteRequest body, AuthenticatedUser principal) {
        var scope = access.requireManager(principal);
        Form form = requireForm(id, scope);
        access.requireManagement(form, scope);
        requireMutable(form);
        if (body == null || (body.status() != null && body.status() != form.getStatus())) {
            throw new BusinessException("A edição preserva o status; use a publicação para enviar um rascunho");
        }
        validation.validate(body, form.getStatus() == FormStatus.DRAFT ? FormValidationMode.DRAFT : FormValidationMode.PUBLISH);
        validation.validateIdentifiers(body, form.getQuestions() == null ? List.of() : form.getQuestions());
        references.validate(body, scope);
        Long expectedVersion = form.getVersion();
        mapper.applyContent(body, form);
        form.setUpdatedAt(now());
        return mapper.toDetails(formRepository.update(form, scope, expectedVersion)
                .orElseThrow(() -> new ConflictException("O formulário foi alterado; consulte novamente antes de salvar")));
    }

    public void delete(String id, AuthenticatedUser principal) {
        var scope = access.requireManager(principal);
        Form form = requireForm(id, scope);
        access.requireManagement(form, scope);
        requireMutable(form);
        if (!formRepository.delete(form, scope)) {
            throw new ConflictException("O formulário foi alterado; consulte novamente antes de excluir");
        }
    }

    public FormDetailsResponse publish(String id, AuthenticatedUser principal) {
        var scope = access.requireManager(principal);
        Form form = requireForm(id, scope);
        access.requireManagement(form, scope);
        requireMutable(form);
        if (form.getStatus() != FormStatus.DRAFT) {
            throw new ConflictException("Somente rascunhos podem ser publicados");
        }
        FormWriteRequest content;
        try {
            content = mapper.toWriteRequest(form);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Tipo de pergunta inválido no rascunho");
        }
        validation.validate(content, FormValidationMode.PUBLISH);
        references.validate(content, scope);
        form.setUpdatedAt(now());
        return mapper.toDetails(formRepository.publish(form, scope)
                .orElseThrow(() -> new ConflictException("O formulário foi alterado; consulte novamente antes de publicar")));
    }

    private void requireMutable(Form form) {
        if ((form.getStatus() != FormStatus.DRAFT && form.getStatus() != FormStatus.PENDING)
                || form.getRespondedAt() != null || (form.getQuestions() != null && form.getQuestions().stream()
                .anyMatch(question -> question.getAnswer() != null && !question.getAnswer().isEmpty()))) {
            throw new ConflictException("Formulários concluídos ou com respostas não podem ser alterados ou excluídos");
        }
    }

    public FormPageResponse findAll(AuthenticatedUser principal, String search, FormStatus status, int page, int size) {
        var scope = access.requireManager(principal);
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 240)) {
            throw new BusinessException("Use page >= 0, size entre 1 e 100 e busca de até 240 caracteres");
        }
        var listing = formRepository.findAll(scope, search, status, page, size);
        return mapper.toPage(listing.forms(), page, size, listing.total());
    }

    public List<Form> findTop3PendingForManager(Long managerId) {
        return formRepository.findTop3PendingByManagerId(managerId);
    }

    public FormDetailsResponse findById(String id, AuthenticatedUser principal) {
        return mapper.toDetails(requireForm(id, access.requireManager(principal)));
    }

    private Form requireForm(String id, FormScope scope) {
        if (id == null || id.length() != 36) {
            throw new BusinessException("O identificador do formulário deve ser um UUID válido");
        }
        try {
            id = UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("O identificador do formulário deve ser um UUID válido");
        }
        return formRepository.findById(id, scope)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));
    }

    private Instant now() {
        // BSON dates preserve milliseconds; use that precision in the initial HTTP response too.
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
