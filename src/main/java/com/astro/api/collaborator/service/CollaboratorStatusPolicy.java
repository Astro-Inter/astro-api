package com.astro.api.collaborator.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class CollaboratorStatusPolicy {
    public void validate(User collaborator, UserStatus desiredStatus) {
        if (desiredStatus == null) {
            throw new BusinessException("O status é obrigatório");
        }
        if (desiredStatus == collaborator.getStatus()) {
            return;
        }
        if (desiredStatus == UserStatus.PRE_CADASTRADO) {
            throw new BusinessException("Não é permitido retornar um colaborador ao pré-cadastro");
        }
        if (desiredStatus == UserStatus.ATIVO) {
            if (collaborator.getStatus() == UserStatus.PRE_CADASTRADO) {
                throw new BusinessException("Conclua o pré-cadastro pelo fluxo de ativação");
            }
            if (collaborator.getFirebaseUid() == null || collaborator.getFirebaseUid().isBlank()) {
                throw new BusinessException("Não é permitido ativar um colaborador sem autenticação concluída");
            }
            if (collaborator.getRole() == null || !collaborator.getRole().isActive()
                    || collaborator.getUnit() == null || !collaborator.getUnit().isActive) {
                throw new BusinessException("Cargo e unidade devem estar ativos para ativar o colaborador");
            }
        }
    }
}
