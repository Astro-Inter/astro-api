package com.astro.api.workspace.service;

import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.Mockito.*;

class PreRegistrationQueueServiceTest {
    @Test
    void resendUsesTheExistingQueueAndRequiresRedisAcknowledgement() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ListOperations<String, String> operations = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(operations);
        PreRegistrationQueueService service = new PreRegistrationQueueService(redis);
        when(operations.rightPush("processamento:email:colaborador", "ana@astro.com")).thenReturn(1L);
        service.enqueueCollaboratorEmail("ana@astro.com");
        verify(operations).rightPush("processamento:email:colaborador", "ana@astro.com");
        when(operations.rightPush(anyString(), anyString())).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.enqueueCollaboratorEmail("ana@astro.com"));
        when(operations.rightPush(anyString(), anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Indisponível"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.data.redis.RedisConnectionFailureException.class,
                () -> service.enqueueCollaboratorEmail("ana@astro.com"));
    }

    @Test
    void sendsAllCollaboratorEmailsInOneRedisListOperation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOperations);
        PreRegistrationQueueService service = new PreRegistrationQueueService(redis);

        service.enqueueAfterCommit(new PreRegisteredCollaboratorsCreatedEvent(List.of("ana@astro.com", "bia@astro.com")));

        verify(listOperations).rightPushAll("processamento:email:colaborador", List.of("ana@astro.com", "bia@astro.com"));
        verifyNoMoreInteractions(listOperations);
    }

    @Test
    void sendsManagerEmailToManagerQueue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOperations);
        PreRegistrationQueueService service = new PreRegistrationQueueService(redis);

        service.enqueueManagerEmail("gestor@astro.com");

        verify(listOperations).rightPush("processamento:email:espaco_trabalho", "gestor@astro.com");
        verifyNoMoreInteractions(listOperations);
    }
}
