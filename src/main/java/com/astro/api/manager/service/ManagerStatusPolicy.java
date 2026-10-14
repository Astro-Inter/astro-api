package com.astro.api.manager.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class ManagerStatusPolicy {
    public void validate(User manager, UserStatus desired) {
        if (desired == null) throw new BusinessException("O status é obrigatório");
        if (desired == manager.getStatus()) return;
        if (desired == UserStatus.PRE_CADASTRADO) {
            throw new BusinessException("Não é permitido retornar um gestor ao pré-cadastro");
        }
        if (desired == UserStatus.ATIVO) {
            if (manager.getStatus() == UserStatus.PRE_CADASTRADO) {
                throw new BusinessException("Conclua o pré-cadastro pelo fluxo de ativação");
            }
            if (manager.getFirebaseUid() == null || manager.getFirebaseUid().isBlank()) {
                throw new BusinessException("Não é permitido ativar um gestor sem autenticação concluída");
            }
        }
    }
}
