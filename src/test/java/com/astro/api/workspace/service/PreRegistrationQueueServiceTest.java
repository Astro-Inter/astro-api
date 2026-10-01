package com.astro.api.workspace.service;

import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.Mockito.*;

class PreRegistrationQueueServiceTest {
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
}
