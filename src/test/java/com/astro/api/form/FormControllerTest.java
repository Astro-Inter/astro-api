package com.astro.api.form;

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
        when(service.findAll(principal, "Clima", FormStatus.PENDENTE, 1, 4)).thenReturn(
                new FormPageResponse(List.of(new FormSummaryResponse(1L, "Clima", "Pesquisa", 3, FormStatus.PENDENTE)), 1, 4, 9, 3));
        mvc.perform(get("/forms").param("search", "Clima").param("status", "PENDENTE").param("page", "1").param("size", "4")
                        .param("workspaceId", "999").param("firebaseUid", "fake"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].titulo").value("Clima"))
                .andExpect(jsonPath("$.data.items[0].quantidade_perguntas").value(3))
                .andExpect(jsonPath("$.data.items[0].perguntas").doesNotExist())
                .andExpect(jsonPath("$.data.totalPages").value(3));
        verify(service).findAll(principal, "Clima", FormStatus.PENDENTE, 1, 4);
    }

    @Test void rejectsMalformedPagingAndUnknownStatus() throws Exception {
        mvc.perform(get("/forms").param("page", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/forms").param("status", "OTHER")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void detailsUsePortugueseFieldsAndStandardErrors() throws Exception {
        var form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.RASCUNHO);
        when(service.findById(1L, principal)).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(get("/forms/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id_gestor").value(20))
                .andExpect(jsonPath("$.data.perguntas[0].id_pergunta").value(1))
                .andExpect(jsonPath("$.data.perguntas[0].resposta").isEmpty());
        mvc.perform(get("/forms/invalid")).andExpect(status().isBadRequest());
        when(service.findById(99L, principal)).thenThrow(new com.astro.api.common.exception.ResourceNotFoundException("Formulário não encontrado"));
        mvc.perform(get("/forms/99")).andExpect(status().isNotFound());
    }

    @Test void creationUsesPortugueseContractAndIgnoresForgedServerFieldsAndAnswers() throws Exception {
        var form = FormTestFixtures.form(42, 1, 10, 20, FormStatus.PENDENTE);
        when(service.create(any(), eq(principal))).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(post("/forms").contentType("application/json").content("""
                {"titulo":"Inspeção","id_gestor":20,"status":"PENDENTE","id_workspace":999,
                 "id_gestor_remetente":999,"respondido_em":"2026-10-15T12:00:00Z",
                 "perguntas":[{"tipo":"texto","pergunta":"Descrição","obrigatoria":true,"resposta":["FORJADA"]}]}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/forms/42"))
                .andExpect(jsonPath("$.data.id_workspace").value(1))
                .andExpect(jsonPath("$.data.id_gestor_remetente").value(10));
        var request = org.mockito.ArgumentCaptor.forClass(com.astro.api.form.dto.request.FormWriteRequest.class);
        verify(service).create(request.capture(), eq(principal));
        assertEquals(com.astro.api.form.model.FormQuestionType.TEXTO, request.getValue().questions().getFirst().type());
    }

    @Test void creationRejectsUnknownTypesImpossibleDatesAndOutOfRangeConfigurations() throws Exception {
        for (String body : List.of("null", "{\"id_gestor\":0}", "{\"data_limite\":\"2026-02-30\"}",
                "{\"perguntas\":[{\"tipo\":\"unknown\"}]}", "{\"perguntas\":[{}]}",
                "{\"perguntas\":[{\"tipo\":\"foto\",\"maximo_arquivos\":11}]}")) {
            mvc.perform(post("/forms").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void updateUsesValidatedIdentityAndMapsConcurrentConflicts() throws Exception {
        var form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.RASCUNHO);
        when(service.update(eq(1L), any(), eq(principal))).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(put("/forms/1").contentType("application/json").content("{\"titulo\":\"Rascunho\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RASCUNHO"));
        when(service.update(eq(1L), any(), eq(principal))).thenThrow(new com.astro.api.common.exception.ConflictException("Alterado"));
        mvc.perform(put("/forms/1").contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/forms/invalid").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void deletionReturnsNoContentAndKeepsNotFoundAndConflictSemantics() throws Exception {
        mvc.perform(delete("/forms/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(1L, principal);
        doThrow(new com.astro.api.common.exception.ResourceNotFoundException("Formulário não encontrado")).when(service).delete(99L, principal);
        mvc.perform(delete("/forms/99")).andExpect(status().isNotFound());
        doThrow(new com.astro.api.common.exception.ConflictException("Concluído")).when(service).delete(2L, principal);
        mvc.perform(delete("/forms/2")).andExpect(status().isConflict());
    }

    @Test void publicationDoesNotAcceptAReplacementFormOrAnswersAndMapsItsStateErrors() throws Exception {
        var form = FormTestFixtures.form(1, 1, 10, 20, FormStatus.PENDENTE);
        when(service.publish(1L, principal)).thenReturn(new com.astro.api.form.mapper.FormMapper().toDetails(form));
        mvc.perform(post("/forms/1/publish").contentType("application/json").content("{\"status\":\"CONCLUIDO\",\"resposta\":[\"Forjada\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDENTE"))
                .andExpect(jsonPath("$.data.perguntas[0].resposta").isEmpty());
        verify(service).publish(1L, principal);
        when(service.publish(1L, principal)).thenThrow(new com.astro.api.common.exception.ConflictException("Já publicado"));
        mvc.perform(post("/forms/1/publish")).andExpect(status().isConflict());
        mvc.perform(post("/forms/invalid/publish")).andExpect(status().isBadRequest());
    }
}
