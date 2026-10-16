package com.astro.api.form.service;

import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.form.dto.request.FormWriteRequest;
import com.astro.api.form.model.FormScope;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormReferenceService {
    private final UserRepository users;
    private final UnitRepository units;
    private final NrCatalogRepository nrs;

    public FormReferenceService(UserRepository users, UnitRepository units, NrCatalogRepository nrs) {
        this.users = users; this.units = units; this.nrs = nrs;
    }

    @Transactional(readOnly = true)
    public void validate(FormWriteRequest body, FormScope scope) {
        if (body.managerId() != null) {
            User manager = users.findById(body.managerId()).filter(user -> user.getUnit() != null
                    && user.getUnit().workspace != null && scope.workspaceId().equals(user.getUnit().workspace.id)
                    && (user.getType() == UserType.GESTOR || user.getType() == UserType.GESTOR_WORKSPACE))
                    .orElseThrow(() -> new ResourceNotFoundException("Gestor destinatário não encontrado no workspace"));
            if (manager.getStatus() != UserStatus.ATIVO || !manager.getUnit().isActive) {
                throw new BusinessException("O gestor destinatário e sua unidade devem estar ativos");
            }
        }
        if (body.unitId() != null) {
            Unit unit = units.findById(body.unitId()).filter(value -> value.workspace != null
                    && scope.workspaceId().equals(value.workspace.id))
                    .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
            if (!unit.isActive) throw new BusinessException("A unidade vinculada deve estar ativa");
        }
        if (body.nrCode() != null) {
            NrCatalog nrCatalogEntry = nrs.findById(body.nrCode()).orElseThrow(() -> new ResourceNotFoundException("NR não encontrada"));
            if (nrCatalogEntry.isRevoked()) throw new BusinessException("A NR vinculada não pode estar revogada");
        }
    }
}
