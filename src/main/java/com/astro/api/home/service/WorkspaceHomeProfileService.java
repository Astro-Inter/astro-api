package com.astro.api.home.service;

import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.repository.HomeUserIdProjection;
import com.astro.api.user.service.UserAvatarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceHomeProfileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceHomeProfileService.class);
    private final HomeRepository homeRepository;
    private final UserAvatarService userAvatarService;

    public WorkspaceHomeProfileService(HomeRepository homeRepository, UserAvatarService userAvatarService) {
        this.homeRepository = homeRepository;
        this.userAvatarService = userAvatarService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String findPhotoUrl(String firebaseUid) {
        try {
            return homeRepository.findByFirebaseUid(firebaseUid)
                    .map(HomeUserIdProjection::getId)
                    .map(userAvatarService::findUrl)
                    .orElse(null);
        } catch (RuntimeException exception) {
            LOGGER.warn("Foto da Home indisponível; retornando os indicadores sem foto");
            return null;
        }
    }
}
