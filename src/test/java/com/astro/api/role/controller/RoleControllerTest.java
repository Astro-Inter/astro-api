package com.astro.api.role.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.role.service.RoleService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoleControllerTest {
    private final RoleService service = mock(RoleService.class);
    private final AuthenticatedUser user = new AuthenticatedUser("gestor", "gestor@astro.com", Role.GESTOR);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RoleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory factory) {
                        return user;
                    }
                }).build();
    }

    @Test
    void exposesPortugueseFieldsWithinTheStandardApiEnvelope() throws Exception {
        when(service.findAll("gestor", user)).thenReturn(List.of(new RoleResponse(12L, 3L, "Gerente", 25, "Ativo")));
        mvc.perform(get("/roles").param("firebase_uid", "gestor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id_cargo").value(12))
                .andExpect(jsonPath("$.data[0].id_workspace").value(3))
                .andExpect(jsonPath("$.data[0].nome").value("Gerente"))
                .andExpect(jsonPath("$.data[0].quantidade_colaboradores").value(25));
    }

    @Test
    void resolvesNestedNrRoutesSeparately() throws Exception {
        when(service.findNrs("gestor", 12L, user)).thenReturn(List.of());
        when(service.findNrOptions("gestor", 12L, user)).thenReturn(List.of());
        mvc.perform(get("/roles/12/nrs").param("firebase_uid", "gestor")).andExpect(status().isOk());
        mvc.perform(get("/roles/12/nrs/options").param("firebase_uid", "gestor")).andExpect(status().isOk());
        verify(service).findNrs("gestor", 12L, user);
        verify(service).findNrOptions("gestor", 12L, user);
    }

    @Test
    void returnsForbiddenForAnIdentityMismatch() throws Exception {
        when(service.findAll("outro", user)).thenThrow(new AccessDeniedException("UID inválido"));
        mvc.perform(get("/roles").param("firebase_uid", "outro"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesAllMutationRoutesAndTheFinalStatus() throws Exception {
        String body = """
                {"firebase_uid":"gestor","nome":"Gerente","status":"Ativo"}
                """;
        mvc.perform(post("/roles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(put("/roles/12").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        when(service.toggleStatus("gestor", 12L, user)).thenReturn(false);
        mvc.perform(patch("/roles/12/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"gestor\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
        mvc.perform(post("/roles/12/nrs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"gestor\",\"nrs\":[{\"nr\":17,\"ativo\":false}]}"))
                .andExpect(status().isOk());
        verify(service).create(any(), eq(user));
        verify(service).update(eq(12L), any(), eq(user));
        verify(service).updateNrs(eq(12L), any(), eq(user));
    }

    @Test
    void rejectsInvalidStatusAndIncompleteNrChanges() throws Exception {
        mvc.perform(post("/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"gestor\",\"nome\":\"Gerente\",\"status\":\"desconhecido\"}"))
                .andExpect(status().isBadRequest());
        for (String nrs : List.of("null", "[null]", "[{\"nr\":1}]", "[{\"nr\":0,\"ativo\":true}]")) {
            mvc.perform(post("/roles/12/nrs").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firebase_uid\":\"gestor\",\"nrs\":" + nrs + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void permitsAnEmptyNrPatchWithoutReplacingExistingLinks() throws Exception {
        mvc.perform(post("/roles/12/nrs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"gestor\",\"nrs\":[]}"))
                .andExpect(status().isOk());
        verify(service).updateNrs(eq(12L), any(), eq(user));
    }

    @Test
    void rejectsMissingUidAndMalformedCargoIdAsBadRequests() throws Exception {
        mvc.perform(get("/roles")).andExpect(status().isBadRequest());
        mvc.perform(get("/roles/abc/nrs").param("firebase_uid", "gestor"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void resolvesCargoAndStatusValidationMessagesFromTheResourceBundle() throws Exception {
        mvc.perform(post("/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"\",\"nome\":\"\",\"status\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("O Firebase UID é obrigatório"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("O nome do cargo é obrigatório"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("O status do cargo é obrigatório"))));
        mvc.perform(patch("/roles/12/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("O Firebase UID é obrigatório"))));
        mvc.perform(put("/roles/12").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"gestor\",\"nome\":\"Gerente\",\"status\":\"outro\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("O status do cargo deve ser Ativo ou Inativo"))));
        verifyNoInteractions(service);
    }

    @Test
    void resolvesMessagesForTheNrListAndItsNestedConstraints() throws Exception {
        assertNrValidationError("null", "A lista de NRs é obrigatória");
        assertNrValidationError("[null]", "Os itens da lista de NRs não podem ser nulos");
        assertNrValidationError("[{}]", "O código da NR é obrigatório");
        assertNrValidationError("[{\"nr\":0,\"ativo\":true}]", "O código da NR deve ser um inteiro positivo");
        assertNrValidationError("[{\"nr\":1}]", "Informe se a NR deve ser vinculada ao cargo");
        verifyNoInteractions(service);
    }

    private void assertNrValidationError(String nrs, String message) throws Exception {
        mvc.perform(post("/roles/12/nrs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebase_uid\":\"gestor\",\"nrs\":" + nrs + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString(message))));
    }
}
