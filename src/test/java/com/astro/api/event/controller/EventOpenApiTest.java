package com.astro.api.event.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.MockMvc;
import com.astro.api.event.service.EventService;
import com.astro.api.config.OpenApiConfig;
import com.astro.api.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springdoc.core.configuration.*;
import org.springdoc.core.properties.*;
import org.springdoc.webmvc.core.configuration.*;
import org.springdoc.webmvc.ui.SwaggerConfig;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventOpenApiTest {
    @Test void generatesEventOperationsEnvelopesAndUpdatedSchemas() throws Exception {
        try(AnnotationConfigWebApplicationContext context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
            MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/events/{eventId}'].get.responses['200']").exists())
                    .andExpect(jsonPath("$.paths['/events'].get.parameters[0].required").value(true))
                    .andExpect(jsonPath("$.paths['/events'].post.responses['201']").exists())
                    .andExpect(jsonPath("$.paths['/events/eligible-candidates'].get.responses['404']").exists())
                    .andExpect(jsonPath("$.paths['/events/{eventId}'].patch.responses['401']").exists())
                    .andExpect(jsonPath("$.paths['/events/{eventId}/cancellation'].patch.responses['401']").exists())
                    .andExpect(jsonPath("$.components.schemas.EventResponse.properties.managerId").exists())
                    .andExpect(jsonPath("$.components.schemas.EventResponse.properties.canEdit.type").value("boolean"))
                    .andExpect(jsonPath("$.components.schemas.EventClassResponse.properties.editable.type").value("boolean"))
                    .andExpect(jsonPath("$.components.schemas.EventParticipantResponse.properties.participationId").exists())
                    .andExpect(jsonPath("$.components.schemas.EventCalendarResponse.properties.status").exists())
                    .andExpect(jsonPath("$.components.schemas.EventCandidateResponse.properties.workModel").exists());
            JsonNode root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsByteArray());
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/event-openapi.json"),root.toPrettyString());
            for(String[] operation:new String[][]{{"/events/{eventId}","get","200"},{"/events","get","200"},{"/events/eligible-candidates","get","200"},{"/events","post","201"},{"/events/{eventId}","patch","200"},{"/events/{eventId}/cancellation","patch","200"}}) {
                JsonNode content=root.path("paths").path(operation[0]).path(operation[1]).path("responses").path(operation[2]).path("content").path("application/json");
                assertFalse(content.path("schema").path("$ref").asText().isBlank(),operation[0]+" response envelope: "+content.path("schema"));
                String name=content.path("schema").path("$ref").asText().substring("#/components/schemas/".length());
                assertTrue(root.path("components").path("schemas").path(name).path("properties").has("data"));

            }
            assertEquals("Treinamento NR 10",root.path("components").path("schemas").path("CreateEventRequest").path("properties").path("title").path("example").asText());
            assertEquals("2026-11-05T08:00:00",root.path("components").path("schemas").path("CreateEventClassRequest").path("properties").path("startDate").path("example").asText());
            assertEquals("Treinamento reagendado",root.path("components").path("schemas").path("CancelEventRequest").path("properties").path("reason").path("example").asText());
            JsonNode required=root.path("components").path("schemas").path("UpdateEventRequest").path("required");
            assertTrue(required.toString().contains("eventClasses"));
            JsonNode properties=root.path("components").path("schemas").path("UpdateEventRequest").path("properties");
            assertFalse(properties.has("nrId"));assertFalse(properties.has("managerId"));assertFalse(properties.has("completionMode"));
        }
    }
    @Configuration @EnableWebMvc
    @Import({EventController.class,GlobalExceptionHandler.class,OpenApiConfig.class,
            SpringDocConfiguration.class,SpringDocConfigProperties.class,SpringDocSpecPropertiesConfiguration.class,
            SpringDocWebMvcConfiguration.class,MultipleOpenApiSupportConfiguration.class,
            SwaggerConfig.class,SwaggerUiConfigProperties.class,SwaggerUiOAuthProperties.class})
    static class Config {
        @Bean EventService service() {return mock(EventService.class);}
    }
}
