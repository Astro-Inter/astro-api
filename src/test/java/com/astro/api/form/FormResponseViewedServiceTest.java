package com.astro.api.form;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.service.ViewedService;
import com.astro.api.conformity.service.FormConformityService;
import com.astro.api.form.dto.response.FormReceivedPageResponse;
import com.astro.api.form.dto.response.FormResponseDetailsResponse;
import com.astro.api.form.mapper.FormAnswerMapper;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.mapper.FormResponseMapper;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.repository.FormReadFilter;
import com.astro.api.form.repository.FormRepository;
import com.astro.api.form.repository.FormRepositoryCustom;
import com.astro.api.form.service.FormAccessService;
import com.astro.api.form.service.FormAnswerValidationService;
import com.astro.api.form.service.FormImageStorageService;
import com.astro.api.form.service.FormLocationService;
import com.astro.api.form.service.FormResponseService;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormResponseViewedServiceTest {
    FormRepository repository;
    SetOperations<String, String> operations;
    FormResponseService service;
    Form form;
    final AuthenticatedUser recipient = new AuthenticatedUser("recipient", "recipient@example.test", Role.GESTOR);

    @BeforeEach
    void setUp() {
        repository = mock(FormRepository.class);
        UserRepository users = mock(UserRepository.class);
        UnitRepository units = mock(UnitRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        operations = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(operations);
        when(users.findByFirebaseUid("recipient"))
                .thenReturn(Optional.of(FormTestFixtures.manager(20, 1, UserType.GESTOR)));
        service = new FormResponseService(repository, new FormAccessService(users),
                mock(FormAnswerValidationService.class), new FormAnswerMapper(),
                new FormResponseMapper(new FormMapper()), mock(FormImageStorageService.class),
                users, units, mock(Validator.class), new FormLocationService(units),
                new ViewedService(redis), mock(FormConformityService.class));
        form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING);
    }

    @Test
    void nonEmptyReceivedPageReadsViewedFlagsFromThePortugueseRedisKey() {
        Form unseen = FormTestFixtures.form(2, 1, 10, 20, FormStatus.PENDING);
        FormReadFilter filter = new FormReadFilter(1L, null, 20L,
                List.of(FormStatus.PENDING, FormStatus.COMPLETED));
        when(repository.findReceived(filter, null, null, null, 0, 100))
                .thenReturn(new FormRepositoryCustom.Listing(List.of(form, unseen), 2));
        when(operations.isMember("formularios:visualizados:20", new Object[]{form.getId(), unseen.getId()}))
                .thenReturn(Map.of(form.getId(), true, unseen.getId(), false));

        FormReceivedPageResponse page = service.findReceived(recipient, null, null, null, 0, 100);

        assertEquals(0, page.page());
        assertEquals(100, page.size());
        assertEquals(2, page.totalElements());
        assertEquals(2, page.items().size());
        assertTrue(page.items().get(0).viewed());
        assertFalse(page.items().get(1).viewed());
        verify(operations).isMember("formularios:visualizados:20", new Object[]{form.getId(), unseen.getId()});
        verifyNoMoreInteractions(operations);
    }

    @Test
    void receivedDetailsMarkTheFormViewedInTheSamePortugueseRedisKey() {
        FormReadFilter filter = new FormReadFilter(1L, null, 20L,
                List.of(FormStatus.PENDING, FormStatus.COMPLETED));
        when(repository.findById(form.getId(), filter)).thenReturn(Optional.of(form));

        FormResponseDetailsResponse details = service.findReceivedById(form.getId(), recipient);

        assertEquals(form.getId(), details.form().id());
        verify(repository).findById(form.getId(), filter);
        verify(operations).add("formularios:visualizados:20", form.getId());
        verifyNoMoreInteractions(operations);
    }
}
