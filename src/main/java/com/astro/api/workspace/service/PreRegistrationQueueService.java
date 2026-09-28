package com.astro.api.workspace.service;

import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class PreRegistrationQueueService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreRegistrationQueueService.class);
    private static final String COLLABORATOR_EMAIL_QUEUE = "processamento:email:colaborador";
    private final StringRedisTemplate redisTemplate;

    public PreRegistrationQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueueAfterCommit(PreRegisteredCollaboratorsCreatedEvent event) {
        if (event.emails().isEmpty()) return;
        try {
            redisTemplate.opsForList().rightPushAll(COLLABORATOR_EMAIL_QUEUE, event.emails());
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao enfileirar {} e-mails de colaboradores após commit", event.emails().size(), exception);
        }
    }
}
