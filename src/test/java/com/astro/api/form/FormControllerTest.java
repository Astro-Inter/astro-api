package com.astro.api.form;

import com.astro.api.form.dto.request.FormCreateOrUpdateRequest;
import org.mockito.ArgumentCaptor;
import com.astro.api.form.model.Form;
import com.astro.api.auth.security.*;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.form.controller.FormController;
import com.astro.api.form.dto.response.*;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static com.astro.api.form.FormTestFixtures.formId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FormControllerTest {
    final FormService service = mock(FormService.class);
    final AuthenticatedUser principal = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR);
    MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FormController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test void listsWithExplicitPagingAndValidatedIdentity() throws Exception {
        when(service.findAll(principal, "Clima", FormStatus.PENDING, 1, 4)).thenReturn(
                new FormPageResponse(List.of(new FormSummaryResponse(formId(1), "Clima", "Pesquisa", 3, FormStatus.PENDING)), 1, 4, 9, 3));
        mvc.perform(get("/forms").param("search", "Clima").param("status", "PENDENTE").param("page", "1").param("size", "4")
                        .param("workspaceId", "999").param("firebaseUid", "fake"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].title").value("Clima"))
                .andExpect(jsonPath("$.data.items[0].questionCount").value(3))
                .andExpect(jsonPath("$.data.items[0].questions").doesNotExist())
                .andExpect(jsonPath("$.data.totalPages").value(3));
        verify(service).findAll(principal, "Clima", FormStatus.PENDING, 1, 4);
    }

    @Test void rejectsMalformedPagingAndUnknownStatus() throws Exception {
        mvc.perform(get("/forms").param("page", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/forms").param("status", "OTHER")).andExpect(status().isBadRequest());
        mvc.perform(get("/forms").param("status", "DRAFT")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void detailsUseCamelCaseFieldsAndStandardErrors() throws Exception {
        Form form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.DRAFT);
        when(service.findById(formId(1), principal)).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(get("/forms/" + formId(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(formId(1)))
                .andExpect(jsonPath("$.data.managerId").value(20))
                .andExpect(jsonPath("$.data.questions[0].id").value(1))
                .andExpect(jsonPath("$.data.questions[0].answer").isEmpty());
        when(service.findById("invalid", principal)).thenThrow(new com.astro.api.common.exception.BusinessException("UUID inválido"));
        mvc.perform(get("/forms/invalid")).andExpect(status().isBadRequest());
        when(service.findById(formId(99), principal)).thenThrow(new com.astro.api.common.exception.ResourceNotFoundException("Formulário não encontrado"));
        mvc.perform(get("/forms/" + formId(99))).andExpect(status().isNotFound());
    }

    @Test void creationUsesCamelCaseContractAndIgnoresForgedServerFieldsAndAnswers() throws Exception {
        Form form = FormTestFixtures.form(42, 1, 10, 20, FormStatus.PENDING);
        when(service.create(any(), eq(principal))).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(post("/forms").contentType("application/json").content("""
                {"title":"Inspeção","managerId":20,"status":"PENDENTE","workspaceId":999,
                 "senderManagerId":999,"respondedAt":"2026-10-15T12:00:00Z",
                 "questions":[{"type":"texto","question":"Descrição","required":true,"answer":["FORJADA"]}]}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/forms/" + formId(42)))
                .andExpect(jsonPath("$.data.workspaceId").value(1))
                .andExpect(jsonPath("$.data.senderManagerId").value(10));
        ArgumentCaptor<FormCreateOrUpdateRequest> request = org.mockito.ArgumentCaptor.forClass(com.astro.api.form.dto.request.FormCreateOrUpdateRequest.class);
        verify(service).create(request.capture(), eq(principal));
        assertEquals(com.astro.api.form.model.FormQuestionType.TEXT, request.getValue().questions().getFirst().type());
    }

    @Test void creationRejectsUnknownTypesImpossibleDatesAndOutOfRangeConfigurations() throws Exception {
        for (String body : List.of("null", "{\"managerId\":0}", "{\"dueDate\":\"2026-02-30\"}",
                "{\"questions\":[{\"type\":\"unknown\"}]}", "{\"questions\":[{}]}",
                "{\"questions\":[{\"type\":\"foto\",\"maxFiles\":11}]}")) {
            mvc.perform(post("/forms").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void updateUsesValidatedIdentityAndMapsConcurrentConflicts() throws Exception {
        Form form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.DRAFT);
        when(service.update(eq(formId(1)), any(), eq(principal))).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(put("/forms/" + formId(1)).contentType("application/json").content("{\"title\":\"Rascunho\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RASCUNHO"));
        when(service.update(eq(formId(1)), any(), eq(principal))).thenThrow(new com.astro.api.common.exception.ConflictException("Alterado"));
        mvc.perform(put("/forms/" + formId(1)).contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        when(service.update(eq("invalid"), any(), eq(principal))).thenThrow(new com.astro.api.common.exception.BusinessException("UUID inválido"));
        mvc.perform(put("/forms/invalid").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void deletionReturnsNoContentAndKeepsNotFoundAndConflictSemantics() throws Exception {
        mvc.perform(delete("/forms/" + formId(1))).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(formId(1), principal);
        doThrow(new com.astro.api.common.exception.ResourceNotFoundException("Formulário não encontrado")).when(service).delete(formId(99), principal);
        mvc.perform(delete("/forms/" + formId(99))).andExpect(status().isNotFound());
        doThrow(new com.astro.api.common.exception.ConflictException("Concluído")).when(service).delete(formId(2), principal);
        mvc.perform(delete("/forms/" + formId(2))).andExpect(status().isConflict());
    }

    @Test void publicationDoesNotAcceptAReplacementFormOrAnswersAndMapsItsStateErrors() throws Exception {
        Form form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDING);
        when(service.publish(formId(1), principal)).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(post("/forms/" + formId(1) + "/publish").contentType("application/json").content("{\"status\":\"CONCLUIDO\",\"answer\":[\"Forjada\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDENTE"))
                .andExpect(jsonPath("$.data.questions[0].answer").isEmpty());
        verify(service).publish(formId(1), principal);
        when(service.publish(formId(1), principal)).thenThrow(new com.astro.api.common.exception.ConflictException("Já publicado"));
        mvc.perform(post("/forms/" + formId(1) + "/publish")).andExpect(status().isConflict());
        when(service.publish("invalid", principal)).thenThrow(new com.astro.api.common.exception.BusinessException("UUID inválido"));
        mvc.perform(post("/forms/invalid/publish")).andExpect(status().isBadRequest());
    }

    @Test void requestValidationResolvesPortugueseMessagesFromTheResourceBundle() throws Exception {
        mvc.perform(post("/forms").contentType("application/json").content("{\"title\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString(
                        "O título do formulário deve ter no máximo 100 caracteres")));
        mvc.perform(post("/forms").contentType("application/json").content("{\"questions\":[{}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("O tipo da pergunta é obrigatório")));
        verifyNoInteractions(service);
    }
}
