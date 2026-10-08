package com.astro.api.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.event.controller.EventController;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventParticipantResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.service.EventService;
import com.astro.api.user.model.WorkModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventControllerIntegrationTest {
    private EventService eventService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticatedUserArgumentResolver())
                .build();
    }

    @Test
    void listsCalendarItems() throws Exception {
        EventCalendarResponse calendarItem = new EventCalendarResponse(
                1L,
                "Treinamento NR 10",
                2L,
                "Grupo 1",
                LocalDateTime.of(2026, 10, 5, 8, 0),
                LocalDateTime.of(2026, 10, 5, 12, 0),
                List.of(new EventParticipantResponse(3L, "Maria", "maria@astro.com")));
        when(eventService.findAll(any(), any(), anyString())).thenReturn(List.of(calendarItem));

        mockMvc.perform(get("/eventos")
                        .param("startDate", "2026-10-01T00:00:00")
                        .param("endDate", "2026-10-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].eventTitle").value("Treinamento NR 10"))
                .andExpect(jsonPath("$.data[0].className").value("Grupo 1"));

        verify(eventService).findAll(any(), any(), anyString());
    }

    @Test
    void listsEligibleCandidates() throws Exception {
        EventCandidateResponse candidate = new EventCandidateResponse(
                3L,
                "Maria",
                "maria@astro.com",
                4L,
                "Eletricista");
        when(eventService.findEligibleCandidates(anyInt(), anyLong(), anyLong(), any(), anyString()))
                .thenReturn(List.of(candidate));

        mockMvc.perform(get("/eventos/colaboradores-elegiveis")
                        .param("nrId", "10")
                        .param("unitId", "2")
                        .param("roleId", "4")
                        .param("modality", WorkModel.PRESENCIAL.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Maria"));

        verify(eventService).findEligibleCandidates(
                anyInt(), anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void createsCompleteEvent() throws Exception {
        CreateEventRequest request = createRequest();
        when(eventService.create(any(), anyString())).thenReturn(eventResponse());

        mockMvc.perform(post("/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Treinamento NR 10"));

        verify(eventService).create(any(CreateEventRequest.class), anyString());
    }

    @Test
    void updatesCompleteEvent() throws Exception {
        UpdateEventRequest request = new UpdateEventRequest(
                "Treinamento NR 10 atualizado",
                "Descrição atualizada",
                10,
                "https://astro.com/evento",
                CompletionMode.GESTOR,
                true,
                createRequest().eventClasses());
        when(eventService.update(anyLong(), any(), anyString())).thenReturn(eventResponse());

        mockMvc.perform(patch("/eventos/{eventId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).update(anyLong(), any(UpdateEventRequest.class), anyString());
    }

    @Test
    void cancelsEvent() throws Exception {
        when(eventService.cancel(anyLong(), any(), anyString())).thenReturn(eventResponse());

        mockMvc.perform(patch("/eventos/{eventId}/cancelamento", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelEventRequest("Reagendado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).cancel(anyLong(), any(CancelEventRequest.class), anyString());
    }

    private CreateEventRequest createRequest() {
        CreateEventClassRequest eventClass = new CreateEventClassRequest(
                "Grupo 1",
                LocalDateTime.of(2026, 10, 5, 8, 0),
                LocalDateTime.of(2026, 10, 5, 12, 0),
                List.of(3L));
        return new CreateEventRequest(
                "Treinamento NR 10",
                "Descrição",
                10,
                "https://astro.com/evento",
                CompletionMode.GESTOR,
                true,
                List.of(eventClass));
    }

    private EventResponse eventResponse() {
        return new EventResponse(
                1L,
                "Treinamento NR 10",
                "Descrição",
                10,
                "https://astro.com/evento",
                CompletionMode.GESTOR,
                true,
                EventStatus.ATIVO,
                null,
                null,
                List.of());
    }

    private static class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AuthenticatedUser.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer container,
                org.springframework.web.context.request.NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return new AuthenticatedUser("integration-manager", "manager@astro.com", Role.GESTOR);
        }
    }
}
