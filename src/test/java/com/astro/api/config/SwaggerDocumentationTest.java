package com.astro.api.config;

import com.astro.api.common.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SwaggerDocumentationTest {
    @Test
    void generatesDocumentedOperationsFromAllControllerInterfaces() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            Set<Class<?>> dependencies = new HashSet<>();
            for (org.springframework.beans.factory.config.BeanDefinition definition : scanner.findCandidateComponents("com.astro.api")) {
                Class<?> controller = Class.forName(definition.getBeanClassName());
                if (!controller.getPackageName().endsWith(".controller") || controller.isMemberClass()) continue;
                assertTrue(java.util.Arrays.stream(controller.getInterfaces())
                        .anyMatch(type -> type.getSimpleName().endsWith("Swagger")), controller.getName());
                context.register(controller);
                for (Class<?> dependency : controller.getConstructors()[0].getParameterTypes()) {
                    dependencies.add(dependency);
                }
            }
            context.addBeanFactoryPostProcessor(factory -> dependencies.forEach(type ->
                    factory.registerSingleton(type.getName(), mock(type))));
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            byte[] document = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            JsonNode api = new ObjectMapper().readTree(document);
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/astro-openapi.json"), api.toPrettyString());
            int operations = 0;
            for (JsonNode path : api.path("paths")) {
                for (JsonNode operation : path) {
                    if (!operation.has("operationId")) continue;
                    operations++;
                    assertFalse(operation.path("summary").asText().isBlank(), operation.toString());
                    assertFalse(operation.path("tags").toString().contains("controller"), operation.toString());
                    assertFalse(operation.path("responses").isEmpty(), operation.toString());
                    boolean successDocumented = false;
                    java.util.Iterator<String> responseCodes = operation.path("responses").fieldNames();
                    while (responseCodes.hasNext()) {
                        if (responseCodes.next().startsWith("2")) successDocumented = true;
                    }
                    assertTrue(successDocumented, operation.toString());
                    for (JsonNode parameter : operation.path("parameters")) {
                        assertFalse(Set.of("authenticatedUser", "principal", "user")
                                .contains(parameter.path("name").asText()), operation.toString());
                    }
                }
            }
            assertTrue(operations >= 85, "Missing operations: " + operations);
            for (String path : new String[]{"/verify-email", "/activate", "/verify-key", "/register-workspace", "/add-manager-email"}) {
                assertEquals(0, api.path("paths").path(path).path("post").path("security").size(), path);
                assertTrue(api.path("paths").path(path).path("post").has("security"), path);
            }
            assertTrue(api.path("paths").path("/events").path("post").path("responses").has("201"));
            assertTrue(api.path("paths").path("/forms").path("post").path("responses").has("201"));
            assertTrue(api.path("paths").path("/forms/{id}").path("delete").path("responses").has("204"));
            assertTrue(api.path("paths").path("/register-workspace").path("post").path("requestBody")
                    .path("content").has("multipart/form-data"));
            assertEquals("2026-11-01T00:00:00", api.path("paths").path("/events").path("get")
                    .path("parameters").get(0).path("example").asText());
            JsonNode schemas = api.path("components").path("schemas");
            JsonNode managedEvent = schemas.path("ManagedCollaboratorEventResponse").path("properties");
            assertTrue(managedEvent.has("eventId"));
            assertTrue(managedEvent.has("completionStatus"));
            assertTrue(managedEvent.has("completionMode"));
            assertFalse(managedEvent.has("participationStatus"));
            assertEquals("#/components/schemas/ManagedCollaboratorEventResponse", schemas
                    .path("CollaboratorEventsResponse").path("properties").path("events").path("items")
                    .path("$ref").asText());
            JsonNode authenticatedEvent = schemas.path("AuthenticatedCollaboratorEventResponse").path("properties");
            assertTrue(authenticatedEvent.has("id"));
            assertTrue(authenticatedEvent.has("participationStatus"));
            assertFalse(authenticatedEvent.has("completionMode"));
            JsonNode managedResponse = api.path("paths").path("/collaborators/{collaboratorId}/events")
                    .path("get").path("responses").path("200").path("content")
                    .path("*/*").path("schema");
            assertEquals("#/components/schemas/ApiResultCollaboratorEventsResponse", managedResponse.path("$ref").asText());
            assertEquals("#/components/schemas/CollaboratorEventsResponse", schemas
                    .path("ApiResultCollaboratorEventsResponse").path("properties").path("data").path("$ref").asText());
            JsonNode authenticatedResponse = api.path("paths").path("/events/collaborator").path("get")
                    .path("responses").path("200").path("content").path("*/*").path("schema");
            String authenticatedEnvelope = authenticatedResponse.path("$ref").asText()
                    .replace("#/components/schemas/", "");
            assertEquals("#/components/schemas/AuthenticatedCollaboratorEventResponse", schemas
                    .path(authenticatedEnvelope).path("properties").path("data").path("items").path("$ref").asText());
            for (String path : new String[]{"/nrs", "/home/collaborator", "/home/manager", "/user/me",
                    "/collaborators", "/notifications"}) {
                JsonNode responses = api.path("paths").path(path).path("get").path("responses");
                assertTrue(responses.has("403"), path);
                assertFalse(responses.has("401"), path);
            }
            assertEquals("#/components/schemas/ApiErrorResponse", api.path("paths")
                    .path("/collaborators/{collaboratorId}/events").path("get").path("responses")
                    .path("404").path("content").path("*/*").path("schema").path("$ref").asText());
            assertEquals("#/components/schemas/SpreadsheetImportErrorResponse", api.path("paths")
                    .path("/register-workspace").path("post").path("responses").path("422")
                    .path("content").path("*/*").path("schema").path("$ref").asText());
            assertTrue(schemas.path("SpreadsheetImportErrorResponse").path("properties").path("data")
                    .path("items").path("properties").has("row"));
            assertLocalReferencesResolve(api, schemas);
        }
    }

    private static void assertLocalReferencesResolve(JsonNode node, JsonNode schemas) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.asText().startsWith("#/components/schemas/")) {
                String name = reference.asText().substring("#/components/schemas/".length());
                assertTrue(schemas.has(name), "Schema ausente: " + name);
            }
            node.elements().forEachRemaining(child -> assertLocalReferencesResolve(child, schemas));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> assertLocalReferencesResolve(child, schemas));
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({OpenApiConfig.class, GlobalExceptionHandler.class, SpringDocConfiguration.class,
            SpringDocConfigProperties.class, SpringDocSpecPropertiesConfiguration.class,
            SpringDocWebMvcConfiguration.class, MultipleOpenApiSupportConfiguration.class})
    static class Config {
    }
}
