package com.astro.api.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.event.controller.EventController;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventClassRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCompletionResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventCompletionStatus;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.ParticipationStatus;
import com.astro.api.event.service.EventService;
import com.astro.api.user.model.WorkModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    void listsCalendarItemsWithoutParticipants() throws Exception {
        EventCalendarResponse calendarItem = new EventCalendarResponse(
                1L,
                "Treinamento NR 10",
                2L,
                "Grupo 1",
                LocalDateTime.of(2026, 10, 5, 8, 0),
                LocalDateTime.of(2026, 10, 5, 12, 0), EventStatus.CANCELADO);
        when(eventService.findAll(any(), any(), anyString())).thenReturn(List.of(calendarItem));

        mockMvc.perform(get("/events")
                        .param("startDate", "2026-10-01T00:00:00")
                        .param("endDate", "2026-10-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].eventTitle").value("Treinamento NR 10"))
                .andExpect(jsonPath("$.data[0].className").value("Grupo 1"))
                .andExpect(jsonPath("$.data[0].status").value("CANCELADO"))
                .andExpect(jsonPath("$.data[0].participants").doesNotExist());

        verify(eventService).findAll(any(), any(), anyString());
    }

    @Test
    void rejectsMissingMalformedAndInvalidCalendarPeriods() throws Exception {
        mockMvc.perform(get("/events")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/events").param("startDate", "invalid").param("endDate", "2026-11-01T00:00:00"))
                .andExpect(status().isBadRequest());
        when(eventService.findAll(any(), any(), anyString()))
                .thenThrow(new BusinessException("Intervalo inválido"));
        mockMvc.perform(get("/events").param("startDate", "2026-11-01T00:00:00").param("endDate", "2026-10-01T00:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsEligibleCandidatesUsingOnlyOptionalNrFilter() throws Exception {
        EventCandidateResponse candidate = new EventCandidateResponse(
                3L,
                "Maria",
                "maria@astro.com",
                4L,
                "Eletricista", 7L, "Sede", WorkModel.HIBRIDO);
        when(eventService.findEligibleCandidates(anyInt(), anyString())).thenReturn(List.of(candidate));

        mockMvc.perform(get("/events/eligible-candidates").param("nrId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Maria"))
                .andExpect(jsonPath("$.data[0].unitId").value(7))
                .andExpect(jsonPath("$.data[0].workModel").value("HIBRIDO"));

        verify(eventService).findEligibleCandidates(anyInt(), anyString());
    }

    @Test
    void missingNrIsNotFoundAndMalformedNrIsBadRequest() throws Exception {
        when(eventService.findEligibleCandidates(99,"integration-manager"))
                .thenThrow(new ResourceNotFoundException("NR não encontrada"));
        mockMvc.perform(get("/events/eligible-candidates").param("nrId","99")).andExpect(status().isNotFound());
        mockMvc.perform(get("/events/eligible-candidates").param("nrId","NR 35")).andExpect(status().isBadRequest());
    }

    @Test
    void findsEventDetailAndUsesTheApiErrorEnvelope() throws Exception {
        when(eventService.findById(1L, "integration-manager")).thenReturn(eventResponse());
        mockMvc.perform(get("/events/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managerId").value(1))
                .andExpect(jsonPath("$.data.canEdit").value(true));
        when(eventService.findById(2L, "integration-manager"))
                .thenThrow(new ResourceNotFoundException("Evento não encontrado"));
        mockMvc.perform(get("/events/2")).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        when(eventService.findById(3L, "integration-manager"))
                .thenThrow(new AccessDeniedException("Gestor ativo necessário"));
        mockMvc.perform(get("/events/3")).andExpect(status().isForbidden());
        mockMvc.perform(get("/events/invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void listsEventsForAuthenticatedCollaborator() throws Exception {
        CollaboratorEventResponse event = new CollaboratorEventResponse(
                1l,
                "Treinamento NR 10",
                LocalDateTime.of(2026, 10, 5, 12, 0),
                true,
                ParticipationStatus.PENDENTE,
                "Grupo 1",
                EventStatus.ATIVO);
        when(eventService.findForCollaborator(anyString())).thenReturn(List.of(event));

        mockMvc.perform(get("/events/collaborator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].classLimitDate").value("2026-10-05T12:00:00"))
                .andExpect(jsonPath("$.data[0].participationStatus").value("PENDENTE"))
                .andExpect(jsonPath("$.data[0].eventStatus").value("ATIVO"));

        verify(eventService).findForCollaborator(anyString());
    }

    @Test
    void createsCompleteEvent() throws Exception {
        when(eventService.create(any(), anyString())).thenReturn(eventResponse());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).create(any(CreateEventRequest.class), anyString());
    }

    @Test
    void creationValidatesMetadataNestedElementsIdsAndEnumsWithPortugueseMessages() throws Exception {
        Map<String, Object> base=new LinkedHashMap<String,Object>();
        base.put("title","Evento");base.put("description","Descrição");base.put("completionMode","GESTOR");base.put("mandatoryEvidence",false);
        Map<String, Object> item=new LinkedHashMap<String,Object>();item.put("name","Grupo");item.put("startDate","2026-10-15T08:00:00");item.put("endDate","2026-10-15T09:00:00");item.put("participantIds",List.of(2));
        base.put("eventClasses",List.of(item));
        for (Map.Entry<String, String> invalid:List.of(Map.entry("title","😀😀"),Map.entry("description","😀a"),Map.entry("title","  ab  "),Map.entry("title","x".repeat(256)),
                Map.entry("description","  "),Map.entry("description","ab"),
                Map.entry("externalLink","ftp://example.com"),Map.entry("externalLink","https://exa mple.com"),
                Map.entry("externalLink","https://example.com/"+"x".repeat(2048)))) {
            Map<String, Object> body=new LinkedHashMap<>(base);body.put(invalid.getKey(),invalid.getValue());
            MvcResult result=mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Erro de validação")).andReturn();
            Assertions.assertFalse(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("{event."));
        }
        for (Object badIds:List.of(Arrays.asList((Integer)null),List.of(0),List.of(-1),List.of())) {
            Map<String, Object> badItem=new LinkedHashMap<>(item);badItem.put("participantIds",badIds);
            Map<String, Object> body=new LinkedHashMap<>(base);body.put("eventClasses",List.of(badItem));
            mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        Map<String, Object> body=new LinkedHashMap<>(base);body.put("eventClasses",Arrays.asList((Object)null));
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest());
        body=new LinkedHashMap<>(base);body.put("completionMode","UNKNOWN");
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("{" )).andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(eventService);
    }

    @Test
    void collaboratorSendsCompletionWithEvidenceFiles() throws Exception {
        when(eventService.requestCompletion(anyLong(), any(), anyString())).thenReturn(
                new EventCompletionResponse(9L, EventCompletionStatus.PENDENTE, LocalDateTime.of(2026, 10, 16, 10, 0)));
        MockMultipartFile evidence = new MockMultipartFile(
                "files", "certificado.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/events/{eventId}/completion", 1L).file(evidence))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDENTE"));

        verify(eventService).requestCompletion(anyLong(), any(), anyString());
    }

    @Test
    void updatesCompleteEventAndItsClasses() throws Exception {
        UpdateEventRequest request = new UpdateEventRequest(
                "Treinamento NR 10 atualizado",
                "Descrição atualizada",
                "https://astro.com/evento",
                List.of(new UpdateEventClassRequest(
                        2L,
                        "Grupo 1 atualizado",
                        LocalDateTime.of(2026, 10, 6, 8, 0),
                        LocalDateTime.of(2026, 10, 6, 12, 0),
                        List.of(3L))));
        when(eventService.update(anyLong(), any(), anyString())).thenReturn(eventResponse());

        mockMvc.perform(patch("/events/{eventId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).update(anyLong(), any(UpdateEventRequest.class), anyString());
    }

    @Test
    void updateValidatesNullClassesParticipantIdsClassIdsAndMetadata() throws Exception {
        String prefix="{\"title\":\"Evento\",\"description\":\"Descrição\",\"eventClasses\":";
        for(String classes:List.of("null","[]","[null]",
                "[{\"id\":0,\"name\":\"A\",\"startDate\":\"2026-10-15T08:00:00\",\"endDate\":\"2026-10-15T09:00:00\",\"participantIds\":[2]}]",
                "[{\"name\":\"A\",\"startDate\":\"2026-10-15T08:00:00\",\"endDate\":\"2026-10-15T09:00:00\",\"participantIds\":[null]}]",
                "[{\"name\":\"A\",\"startDate\":\"2026-10-15T08:00:00\",\"endDate\":\"2026-10-15T09:00:00\",\"participantIds\":[-1]}]")) {
            mockMvc.perform(patch("/events/1").contentType(MediaType.APPLICATION_JSON).content(prefix+classes+"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Erro de validação"));
        }
        Mockito.verifyNoInteractions(eventService);
    }

    @Test
    void cancelsEvent() throws Exception {
        when(eventService.cancel(anyLong(), any(), anyString())).thenReturn(eventResponse());

        mockMvc.perform(patch("/events/{eventId}/cancellation", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelEventRequest("Reagendado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService).cancel(anyLong(), any(CancelEventRequest.class), anyString());
    }

    @Test
    void cancellationRequiresNormalizedReasonAndResolvesPortugueseMessages() throws Exception {
        for(String reason:List.of(""," "," ab ","😀😀","x".repeat(1001))) {
            MvcResult result=mockMvc.perform(patch("/events/1/cancellation").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new CancelEventRequest(reason))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Erro de validação")).andReturn();
            Assertions.assertFalse(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("{event."));
        }
        mockMvc.perform(patch("/events/1/cancellation").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(eventService);
    }

    @Test
    void updateRejectsDescriptionBelowTheDatabaseMinimumAfterTrimming() throws Exception {
        UpdateEventRequest body=new UpdateEventRequest("Evento","  ab  ",null,List.of(new UpdateEventClassRequest(2L,"Grupo",LocalDateTime.of(2026,10,15,8,0),LocalDateTime.of(2026,10,15,9,0),List.of(3L))));
        mockMvc.perform(patch("/events/1").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Erro de validação"));
        Mockito.verifyNoInteractions(eventService);
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
                1L, true, true, true, List.of());
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
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return new AuthenticatedUser("integration-manager", "manager@astro.com", Role.GESTOR);
        }
    }
}
