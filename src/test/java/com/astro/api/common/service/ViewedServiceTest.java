package com.astro.api.common.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewedServiceTest {
    @ParameterizedTest
    @CsvSource({
            "events,eventos:visualizados:42",
            "notifications,notificacoes:visualizados:42",
            "validations,validacoes:visualizados:42",
            "forms,formularios:visualizados:42"
    })
    void readsAndWritesTheSamePortugueseKeyWithDistinctItemIds(String resource, String key) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SetOperations<String, String> operations = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(operations);
        when(operations.isMember(key, new Object[]{"101", "102"}))
                .thenReturn(Map.of("101", true, "102", false));
        ViewedService service = new ViewedService(redis);

        service.markViewed(42L, resource, 101L);
        Set<String> viewed = service.findViewed(42L, resource, List.of(101L, 101L, 102L));

        verify(operations).add(key, "101");
        verify(operations).isMember(key, new Object[]{"101", "102"});
        assertEquals(Set.of("101"), viewed);
    }
}
