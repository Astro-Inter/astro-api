package com.astro.api.form.service;

import com.astro.api.form.repository.FormRepositoryCustom;
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
import com.astro.api.form.model.FormWriteCondition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class FormService {
    private final FormRepository formRepository;
    private final FormAccessService formAccessService;
    private final FormMapper formMapper;
    private final FormValidationService formValidationService;
    private final FormReferenceService formReferenceService;

    public FormService(FormRepository formRepository,
                       FormAccessService formAccessService,
                       FormMapper formMapper,
                       FormValidationService formValidationService,
                       FormReferenceService formReferenceService) {
        this.formRepository = formRepository;
        this.formAccessService = formAccessService;
        this.formMapper = formMapper;
        this.formValidationService = formValidationService;
        this.formReferenceService = formReferenceService;
    }

    public FormDetailsResponse create(FormWriteRequest formRequest, AuthenticatedUser authenticatedUser) {
        FormScope formScope = formAccessService.requireManager(authenticatedUser);
        if (formRequest == null || (formRequest.status() != null && formRequest.status() != FormStatus.DRAFT && formRequest.status() != FormStatus.PENDING)) {
            throw new BusinessException("Crie o formulário como RASCUNHO ou PENDENTE");
        }
        FormStatus status = formRequest.status() == null ? FormStatus.PENDING : formRequest.status();
        formValidationService.validate(formRequest, status == FormStatus.DRAFT ? FormValidationMode.DRAFT : FormValidationMode.PUBLISH);
        formValidationService.validateIdentifiers(formRequest, List.of());
        formReferenceService.validate(formRequest, formScope);
        Form form = new Form();
        formMapper.applyContent(formRequest, form);
        form.setId(UUID.randomUUID().toString());
        form.setWorkspaceId(formScope.workspaceId());
        form.setSenderManagerId(formScope.managerId());
        form.setStatus(status);
        form.setCreatedAt(now());
        form.setUpdatedAt(form.getCreatedAt());
        form.setVersion(0L);
        return formMapper.toDetails(formRepository.insert(form));
    }

    public FormDetailsResponse update(String formId, FormWriteRequest formRequest, AuthenticatedUser authenticatedUser) {
        FormScope formScope = formAccessService.requireManager(authenticatedUser);
        Form form = requireForm(formId, formScope);
        formAccessService.requireManagement(form, formScope);
        requireMutable(form);
        if (formRequest == null || (formRequest.status() != null && formRequest.status() != form.getStatus())) {
            throw new BusinessException("A edição preserva o status; use a publicação para enviar um rascunho");
        }
        formValidationService.validate(formRequest, form.getStatus() == FormStatus.DRAFT ? FormValidationMode.DRAFT : FormValidationMode.PUBLISH);
        formValidationService.validateIdentifiers(formRequest, form.getQuestions() == null ? List.of() : form.getQuestions());
        formReferenceService.validate(formRequest, formScope);
        FormWriteCondition writeCondition = formAccessService.writeCondition(form, formScope);
        formMapper.applyContent(formRequest, form);
        form.setUpdatedAt(now());
        return formMapper.toDetails(formRepository.update(form, writeCondition)
                .orElseThrow(() -> new ConflictException("O formulário foi alterado; consulte novamente antes de salvar")));
    }

    public void delete(String formId, AuthenticatedUser authenticatedUser) {
        FormScope formScope = formAccessService.requireManager(authenticatedUser);
        Form form = requireForm(formId, formScope);
        formAccessService.requireManagement(form, formScope);
        requireMutable(form);
        if (!formRepository.deleteIfUnanswered(formAccessService.writeCondition(form, formScope))) {
            throw new ConflictException("O formulário foi alterado; consulte novamente antes de excluir");
        }
    }

    public FormDetailsResponse publish(String formId, AuthenticatedUser authenticatedUser) {
        FormScope formScope = formAccessService.requireManager(authenticatedUser);
        Form form = requireForm(formId, formScope);
        formAccessService.requireManagement(form, formScope);
        requireMutable(form);
        if (form.getStatus() != FormStatus.DRAFT) {
            throw new ConflictException("Somente rascunhos podem ser publicados");
        }
        FormWriteRequest savedDraftRequest;
        try {
            savedDraftRequest = formMapper.toWriteRequest(form);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Tipo de pergunta inválido no rascunho");
        }
        formValidationService.validate(savedDraftRequest, FormValidationMode.PUBLISH);
        formReferenceService.validate(savedDraftRequest, formScope);
        FormWriteCondition writeCondition = formAccessService.writeCondition(form, formScope);
        return formMapper.toDetails(formRepository.updateStatus(writeCondition, FormStatus.PENDING, now())
                .orElseThrow(() -> new ConflictException("O formulário foi alterado; consulte novamente antes de publicar")));
    }

    private void requireMutable(Form form) {
        if ((form.getStatus() != FormStatus.DRAFT && form.getStatus() != FormStatus.PENDING)
                || form.getRespondedAt() != null || (form.getQuestions() != null && form.getQuestions().stream()
                .anyMatch(question -> question.getAnswer() != null && !question.getAnswer().isEmpty()))) {
            throw new ConflictException("Formulários concluídos ou com respostas não podem ser alterados ou excluídos");
        }
    }

    public FormPageResponse findAll(AuthenticatedUser authenticatedUser, String search, FormStatus status, int page, int size) {
        FormScope formScope = formAccessService.requireManager(authenticatedUser);
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 240)) {
            throw new BusinessException("Use page >= 0, size entre 1 e 100 e busca de até 240 caracteres");
        }
        String normalizedSearch = search == null ? null : search.strip();
        FormRepositoryCustom.Listing formListing = formRepository.findAll(formAccessService.readFilter(formScope), normalizedSearch, status, page, size);
        return formMapper.toPage(formListing.forms(), page, size, formListing.total());
    }

    public List<Form> findTop3PendingForManager(Long managerId) {
        return formRepository.findTop3PendingByManagerId(managerId);
    }

    public FormDetailsResponse findById(String formId, AuthenticatedUser authenticatedUser) {
        return formMapper.toDetails(requireForm(formId, formAccessService.requireManager(authenticatedUser)));
    }

    private Form requireForm(String formId, FormScope formScope) {
        if (formId == null || formId.length() != 36) {
            throw new BusinessException("O identificador do formulário deve ser um UUID válido");
        }
        try {
            formId = UUID.fromString(formId).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("O identificador do formulário deve ser um UUID válido");
        }
        return formRepository.findById(formId, formAccessService.readFilter(formScope))
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));
    }

    private Instant now() {
        // BSON dates preserve milliseconds; use that precision in the initial HTTP response too.
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
