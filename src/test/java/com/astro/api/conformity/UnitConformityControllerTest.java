package com.astro.api.conformity;

import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.conformity.controller.UnitConformityController;
import com.astro.api.conformity.service.UnitConformityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UnitConformityControllerTest {
    private final UnitConformityService service = mock(UnitConformityService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UnitConformityController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingUnitAndNrReturnTheStandardEnvelopeWithPortugueseMessages() throws Exception {
        mvc.perform(post("/conformities/units").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.path").value("/conformities/units"))
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        "unitId: O identificador da unidade é obrigatório",
                        "nrId: O código da NR é obrigatório")));
        verifyNoInteractions(service);
    }

    @Test
    void zeroAndNegativeIdentifiersReturnPortugueseMessagesWithoutCallingTheService() throws Exception {
        for (String body : java.util.List.of("{\"unitId\":0,\"nrId\":0}", "{\"unitId\":-1,\"nrId\":-1}")) {
            mvc.perform(post("/conformities/units").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", containsInAnyOrder(
                            "unitId: O identificador da unidade deve ser positivo",
                            "nrId: O código da NR deve ser positivo")));
        }
        verifyNoInteractions(service);
    }
}
