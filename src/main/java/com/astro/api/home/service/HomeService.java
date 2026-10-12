package com.astro.api.home.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.home.dto.response.HomeResponse;
import com.astro.api.home.repository.HomeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HomeService {
    private final HomeRepository homeRepository;
    private final JsonMapper objectMapper;

    public HomeService(HomeRepository homeRepository, JsonMapper objectMapper) {
        this.homeRepository = homeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HomeResponse loadHome(AuthenticatedUser user) {
        if (user == null || user.firebaseUid() == null || user.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar a Home");
        }
        String homeJson = homeRepository.loadHome(user.firebaseUid());
        if (homeJson == null) {
            throw new AccessDeniedException("Gestor ativo sem acesso à Home do workspace");
        }
        try {
            HomeResponse response = objectMapper.readValue(homeJson, HomeResponse.class);
            if (response == null || response.userName() == null || response.statistics() == null) {
                throw new IllegalStateException("Dados da Home retornados pelo banco são inválidos");
            }
            return response;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home", exception);
        }
    }
}
