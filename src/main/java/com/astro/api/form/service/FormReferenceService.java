package com.astro.api.form.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.form.dto.request.FormRequest;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormReferenceService {
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final NrCatalogRepository nrCatalogRepository;

    public FormReferenceService(UserRepository userRepository, UnitRepository unitRepository,
                                NrCatalogRepository nrCatalogRepository) {
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.nrCatalogRepository = nrCatalogRepository;
    }

    @Transactional(readOnly = true)
    public void validate(FormRequest formRequest, FormScope formScope) {
        if (formRequest.managerId() != null) {
            User manager = userRepository.findById(formRequest.managerId()).filter(user -> user.getUnit() != null
                    && user.getUnit().workspace != null && formScope.workspaceId().equals(user.getUnit().workspace.id)
                    && (user.getType() == UserType.GESTOR || user.getType() == UserType.GESTOR_WORKSPACE))
                    .orElseThrow(() -> new ResourceNotFoundException("Gestor destinatário não encontrado no workspace"));
            if (manager.getStatus() != UserStatus.ATIVO || !manager.getUnit().isActive) {
                throw new BusinessException("O gestor destinatário e sua unidade devem estar ativos");
            }
        }
        if (formRequest.unitId() != null) {
            Unit unit = unitRepository.findById(formRequest.unitId()).filter(value -> value.workspace != null
                    && formScope.workspaceId().equals(value.workspace.id))
                    .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
            if (!unit.isActive) throw new BusinessException("A unidade vinculada deve estar ativa");
        }
        if (formRequest.nrCode() != null) {
            NrCatalog nrCatalogEntry = nrCatalogRepository.findById(formRequest.nrCode()).orElseThrow(() -> new ResourceNotFoundException("NR não encontrada"));
            if (nrCatalogEntry.isRevoked()) throw new BusinessException("A NR vinculada não pode estar revogada");
        }
    }
}
