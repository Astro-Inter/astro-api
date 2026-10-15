package com.astro.api.form.service;

import com.astro.api.form.model.Form;
import com.astro.api.form.repository.FormRepository;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.response.FormPageResponse;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.form.dto.request.FormWriteRequest;
import com.astro.api.form.strategy.FormValidationMode;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.repository.FormManagementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FormService {
    private final FormRepository formRepository;
    private final FormManagementRepository managementRepository;
    private final FormAccessService access;
    private final FormMapper mapper;
    private final FormValidationService validation;
    private final FormReferenceService references;

    public FormService(FormRepository formRepository, FormManagementRepository managementRepository,
                       FormAccessService access, FormMapper mapper,
                       FormValidationService validation, FormReferenceService references) {
        this.formRepository = formRepository;
        this.managementRepository = managementRepository;
        this.access = access;
        this.mapper = mapper;
        this.validation = validation;
        this.references = references;
    }

    public FormDetailsResponse create(FormWriteRequest body, AuthenticatedUser principal) {
        var scope = access.requireManager(principal);
        if (body == null || (body.status() != null && body.status() != FormStatus.RASCUNHO && body.status() != FormStatus.PENDENTE)) {
            throw new BusinessException("Crie o formulário como RASCUNHO ou PENDENTE");
        }
        FormStatus status = body.status() == null ? FormStatus.PENDENTE : body.status();
        validation.validate(body, status == FormStatus.RASCUNHO ? FormValidationMode.DRAFT : FormValidationMode.PUBLISH);
        references.validate(body, scope);
        Form form = new Form();
        mapper.applyContent(body, form);
        form.setId(managementRepository.nextId());
        form.setWorkspaceId(scope.workspaceId());
        form.setSenderManagerId(scope.managerId());
        form.setStatus(status);
        form.setCreatedAt(java.time.Instant.now());
        form.setUpdatedAt(form.getCreatedAt());
        form.setVersion(0L);
        return mapper.toDetails(managementRepository.insert(form));
    }

    public FormPageResponse findAll(AuthenticatedUser principal, String search, FormStatus status, int page, int size) {
        var scope = access.requireManager(principal);
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 240)) {
            throw new BusinessException("Use page >= 0, size entre 1 e 100 e busca de até 240 caracteres");
        }
        var listing = managementRepository.findAll(scope, search, status, page, size);
        return new FormPageResponse(listing.forms().stream().map(mapper::toSummary).toList(), page, size,
                listing.total(), (listing.total() + size - 1) / size);
    }

    public List<Form> findTop3PendingForManager(Long managerId) {
        return formRepository.findTop3PendingByManagerId(managerId);
    }

    public FormDetailsResponse findById(Long id, AuthenticatedUser principal) {
        return mapper.toDetails(requireForm(id, access.requireManager(principal)));
    }

    private Form requireForm(Long id, FormAccessService.Scope scope) {
        if (id == null || id <= 0) throw new BusinessException("O identificador do formulário deve ser positivo");
        return managementRepository.findById(id, scope)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));
    }
}
