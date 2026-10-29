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
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/astro-openapi.json"), api.toPrettyString());
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
