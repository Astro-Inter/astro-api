package com.astro.api.workspace.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.common.model.SpreadsheetImportError;
import com.astro.api.unit.controller.UnitSettingsController;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.service.UnitSettingsService;
import com.astro.api.workspace.dto.response.*;
import com.astro.api.workspace.service.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkspaceSettingsControllerTest {
    private final WorkspaceSettingsService settings = mock(WorkspaceSettingsService.class);
    private final WorkspaceImportService imports = mock(WorkspaceImportService.class);
    private final WorkspaceManagerTransferService transfers = mock(WorkspaceManagerTransferService.class);
    private final UnitSettingsService units = mock(UnitSettingsService.class);
    private final AuthenticatedUser principal = new AuthenticatedUser("uid", null, Role.GESTOR_WORKSPACE);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkspaceSettingsController(settings, imports, transfers),
                        new UnitSettingsController(units))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) { return principal; }
                }).build();
    }

    @Test
    void transfersTheWorkspaceResponsibilityUsingAnExistingManagerId() throws Exception {
        when(transfers.transfer(any(), eq(principal)))
                .thenReturn(new WorkspaceManagerTransferResponse(3L, 8L, 9L));
        mvc.perform(post("/workspace/manager-transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newManagerId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(3))
                .andExpect(jsonPath("$.data.previousManagerId").value(8))
                .andExpect(jsonPath("$.data.newManagerId").value(9));
        verify(transfers).transfer(argThat(body -> body.newManagerId().equals(9L)), eq(principal));
    }

    @Test
    void rejectsMissingOrInvalidSuccessorBeforeCallingTheService() throws Exception {
        Map<String, String> invalidRequests = Map.of(
                "{}", "O novo gestor é obrigatório",
                "{\"newManagerId\":null}", "O novo gestor é obrigatório",
                "{\"newManagerId\":0}", "O identificador do novo gestor deve ser positivo",
                "{\"newManagerId\":-1}", "O identificador do novo gestor deve ser positivo");
        for (Map.Entry<String, String> invalidRequest : invalidRequests.entrySet()) {
            mvc.perform(post("/workspace/manager-transfer").contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest.getKey()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("newManagerId: " + invalidRequest.getValue()));
        }
        verifyNoInteractions(transfers);
    }

    @Test
    void createsAndUpdatesUnitsThroughTheDocumentedHttpRoutes() throws Exception {
        UnitResponse response = new UnitResponse(12L, 3L, "Filial", "Ativo", 0,
                "01234567", "Rua A", "São Paulo", "Centro", "SP", null, "10A");
        when(units.create(any(), eq(principal))).thenReturn(response);
        when(units.update(eq(12L), any(), eq(principal))).thenReturn(response);
        String body = """
                {"name":"Filial","cep":"01234-567","state":"sp","city":"São Paulo",
                 "neighborhood":"Centro","street":"Rua A","number":"10A"}
                """;
        mvc.perform(post("/units").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/units/12"))
                .andExpect(jsonPath("$.data.numero").value("10A"));
        mvc.perform(put("/units/12").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id_unidade").value(12));
        mvc.perform(post("/units").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"number\":\"10A\"", "\"number\":\"\"")))
                .andExpect(status().isBadRequest());
        verify(units, times(1)).create(any(), eq(principal));
    }

    @Test
    void returnsWorkspaceAndNormalizesFormattedCnpj() throws Exception {
        when(settings.find(principal)).thenReturn(new WorkspaceResponse(3L, "Empresa", "12345678000199"));
        mvc.perform(get("/workspace")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cnpj").value("12345678000199"));
        mvc.perform(put("/workspace").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" Nova empresa \",\"cnpj\":\"12.345.678/0001-99\"}"))
                .andExpect(status().isOk());
        verify(settings).update(argThat(body -> body.name().equals("Nova empresa")
                && body.cnpj().equals("12345678000199")), eq(principal));
    }

    @Test
    void rejectsMalformedCompanyAndNullUnitBeforeService() throws Exception {
        mvc.perform(put("/workspace").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \",\"cnpj\":\"invalid\"}")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/workspace/import")
                .file(new MockMultipartFile("file", "colaboradores.xlsx", "application/octet-stream", new byte[]{1}))
                .file(new MockMultipartFile("data", "", "application/json", "{\"units\":[null]}".getBytes())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(settings, imports);
    }

    @Test
    void supportsFileWithoutAdditionalUnitsAndReturnsStructuredRowErrors() throws Exception {
        when(imports.importFile(any(), any(), eq(principal))).thenThrow(new SpreadsheetImportException(
                List.of(new SpreadsheetImportError(4, "unidade", "Informe endereço da nova unidade"))));
        mvc.perform(multipart("/workspace/import").file(
                new MockMultipartFile("file", "colaboradores.xlsx", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].row").value(4))
                .andExpect(jsonPath("$.data[0].field").value("unidade"));
    }
}
