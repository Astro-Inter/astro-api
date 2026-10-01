package com.astro.api.workspace.controller;

import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.workspace.service.PreRegistrationQueueService;
import com.astro.api.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerTest {

    private PreRegistrationQueueService preRegistrationQueueService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        preRegistrationQueueService = mock(PreRegistrationQueueService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkspaceController(workspaceService, preRegistrationQueueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldEnqueueManagerEmail() throws Exception {
        mockMvc.perform(post("/queue-manager-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gestor@astro.com\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(preRegistrationQueueService).enqueueManagerEmail("gestor@astro.com");
    }

    @Test
    void shouldRejectInvalidManagerEmail() throws Exception {
        mockMvc.perform(post("/queue-manager-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"email-invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0]", containsString("email:")));

        verifyNoInteractions(preRegistrationQueueService);
    }
}
