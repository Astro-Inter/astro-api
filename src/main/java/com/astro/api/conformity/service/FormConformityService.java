package com.astro.api.conformity.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.*;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.repository.*;
import com.astro.api.form.model.*;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.repository.UserRepository;
import java.time.ZoneId;
import java.time.LocalDate;
import com.astro.api.conformity.mapper.UnitConformityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormConformityService {
    private final ConformityRepository writes;
    private final UnitConformityRepository repository;
    private final NrCatalogRepository nrs;
    private final UserRepository users;
    private final UnitConformityMapper mapper;
    public FormConformityService(ConformityRepository writes, UnitConformityRepository repository,
            NrCatalogRepository nrs, UserRepository users, UnitConformityMapper mapper) {
        this.writes = writes;
        this.repository = repository;
        this.nrs = nrs;
        this.users = users;
        this.mapper = mapper;
    }
    @Transactional
    public void synchronizeApproved(Form form, AuthenticatedUser principal) {
        if (form.getReview() == null || form.getReview().status() != FormReviewStatus.APPROVED || form.getNrCode() == null) {
            return;
        }
        if (form.getStatus() != FormStatus.COMPLETED || form.getRespondedAt() == null) {
            throw new BusinessException("Formulário aprovado sem resposta concluída");
        }
        writes.lockWrites();
        if (repository.findByFormResponseId(form.getId()).isPresent()) {
            return;
        }
        Long unitId = form.getUnitId();
        if (unitId == null) {
            User recipient = users.findById(form.getManagerId()).orElseThrow(() -> new ResourceNotFoundException("Gestor destinatário não encontrado"));
            if (recipient.getUnit() == null || recipient.getUnit().workspace == null
                    || !form.getWorkspaceId().equals(recipient.getUnit().workspace.id)) {
                throw new BusinessException("Gestor destinatário sem unidade no workspace do formulário");
            }
            unitId = recipient.getUnit().id;
        }
        Unit unit = repository.findUnitInScope(unitId, form.getWorkspaceId(), principal.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade avaliada não encontrada no escopo autorizado"));
        NrCatalog nr = nrs.findLockedByCodeNr(form.getNrCode()).orElseThrow(() -> new ResourceNotFoundException("NR não encontrada"));
        if (nr.getRefresherFrequency() <= 0) {
            throw new BusinessException("Prazo de reciclagem da NR inválido");
        }
        LocalDate responseDate = form.getRespondedAt().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
        LocalDate expiresAt = responseDate.plusMonths(nr.getRefresherFrequency());
        Conformity conformity = mapper.toApprovedForm(form, unit.id, expiresAt);
        repository.saveAndFlush(conformity);
    }
}
