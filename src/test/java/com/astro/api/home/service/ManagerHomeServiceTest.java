package com.astro.api.home.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.form.model.Form;
import com.astro.api.form.service.FormService;
import com.astro.api.home.dto.response.ManagerHomeResponse;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.repository.HomeUserIdProjection;
import com.astro.api.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerHomeServiceTest {
    private final HomeRepository homeRepository = mock(HomeRepository.class);
    private final FormService formService = mock(FormService.class);
    private final ViewedService viewedService = mock(ViewedService.class);
    private final HomeService service = new HomeService(homeRepository, JsonMapper.builder().build(),
            mock(NotificationService.class), viewedService, formService);
    private final AuthenticatedUser manager = new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR);

    @Test
    void combinesDatabaseMongoAndRedisDataWithoutWritingAnything() {
        when(homeRepository.loadManagerHome("manager")).thenReturn("""
                {"username":"Ana Dias","workspace_name":"Metalúrgica Brasilfer Ltda",
                 "indicators":{"current_events":3,"pending_validations":15,"collaborators_with_pending_events":34},
                 "pending_validations":[
                   {"id":201,"collaborator_name":"João Lima","event_title":"Treinamento de EPI"},
                   {"id":202,"collaborator_name":"Maria Silva","event_title":"NR-12"}]}
                """);
        when(homeRepository.findByFirebaseUid("manager")).thenReturn(Optional.of(userId(2L)));
        when(formService.findTop3PendingForManager(2L)).thenReturn(List.of(form(301L, "Checklist",
                Instant.parse("2026-10-15T12:00:00Z")), form(302L, "Pesquisa", null)));
        when(viewedService.findViewed(2L, "validations", List.of(201L, 202L))).thenReturn(Set.of("202"));
        when(viewedService.findViewed(2L, "forms", List.of(301L, 302L))).thenReturn(Set.of("301"));

        ManagerHomeResponse response = service.loadManagerHome(manager);

        assertEquals("Ana Dias", response.username());
        assertEquals(15, response.indicators().pendingValidations());
        assertEquals(2, response.pendingValidations().size());
        assertFalse(response.pendingValidations().getFirst().viewed());
        assertTrue(response.pendingValidations().get(1).viewed());
        assertTrue(response.pendingForms().getFirst().viewed());
        assertNull(response.pendingForms().get(1).daysLeft());
        verify(viewedService).findViewed(2L, "validations", List.of(201L, 202L));
        verify(viewedService).findViewed(2L, "forms", List.of(301L, 302L));
        verifyNoMoreInteractions(viewedService);
    }

    @Test
    void returnsEmptyListsWhenThereAreNoPendingItems() {
        when(homeRepository.loadManagerHome("manager")).thenReturn("""
                {"username":"Ana Dias","workspace_name":"Workspace",
                 "indicators":{"current_events":0,"pending_validations":0,"collaborators_with_pending_events":0},
                 "pending_validations":[]}
                """);
        when(homeRepository.findByFirebaseUid("manager")).thenReturn(Optional.of(userId(2L)));
        when(formService.findTop3PendingForManager(2L)).thenReturn(List.of());
        when(viewedService.findViewed(2L, "validations", List.of())).thenReturn(Set.of());
        when(viewedService.findViewed(2L, "forms", List.of())).thenReturn(Set.of());

        ManagerHomeResponse response = service.loadManagerHome(manager);

        assertTrue(response.pendingValidations().isEmpty());
        assertTrue(response.pendingForms().isEmpty());
    }

    @Test
    void rejectsNullResultFromTheManagerHomeFunction() {
        when(homeRepository.loadManagerHome("manager")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.loadManagerHome(manager));
        verifyNoInteractions(formService, viewedService);
    }

    @Test
    void calculatesCalendarDaysIncludingTodayAndPastDueDates() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate currentDate = LocalDate.of(2026, 10, 12);

        assertEquals(2, HomeService.daysLeft(Instant.parse("2026-10-14T12:00:00Z"), currentDate, zone));
        assertEquals(0, HomeService.daysLeft(Instant.parse("2026-10-12T12:00:00Z"), currentDate, zone));
        assertEquals(-2, HomeService.daysLeft(Instant.parse("2026-10-10T12:00:00Z"), currentDate, zone));
        assertNull(HomeService.daysLeft(null, currentDate, zone));
    }

    private HomeUserIdProjection userId(Long value) {
        return () -> value;
    }

    private Form form(Long id, String title, Instant dueDate) {
        Form form = new Form();
        form.setId(id);
        form.setTitle(title);
        form.setDueDate(dueDate);
        return form;
    }
}
