package com.astro.api.form;

import com.astro.api.form.dto.request.FormOptionRequest;
import com.astro.api.form.dto.request.FormQuestionDefinitionRequest;
import com.astro.api.form.dto.request.FormRequest;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormQuestionType;
import com.astro.api.form.model.FormStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class FormContractTest {
    @Test void requestsAndResponsesUseCamelCaseIncludingNestedQuestionsAndOptions() {
        JsonMapper mapper = JsonMapper.builder().build();
        FormRequest request = mapper.readValue("""
                {"title":"Inspeção","description":"Condições da unidade","managerId":20,
                 "nrCode":6,"unitId":3,"dueDate":"2026-10-30","status":"PENDENTE",
                 "questions":[
                   {"type":"alternativa","question":"Quais riscos?","required":true,"multiple":true,
                    "options":[{"text":"Queda","other":false},{"text":"","other":true}]},
                   {"type":"foto","question":"Fotos do local","required":false,
                    "maxFiles":3,"maxFileSizeMb":20}]}
                """, FormRequest.class);
        assertEquals("Inspeção", request.title());
        assertEquals("Condições da unidade", request.description());
        assertEquals(20L, request.managerId());
        assertEquals(6, request.nrCode());
        assertEquals(3L, request.unitId());
        assertEquals(LocalDate.of(2026, 10, 30), request.dueDate());
        assertTrue(request.questions().getFirst().required());
        assertTrue(request.questions().getFirst().multiple());
        assertEquals("Queda", request.questions().getFirst().options().getFirst().text());
        assertTrue(request.questions().getFirst().options().get(1).other());
        assertEquals(3, request.questions().get(1).maxFiles());
        assertEquals(20, request.questions().get(1).maxFileSizeMb());

        Form form = new Form();
        FormMapper formMapper = new FormMapper();
        formMapper.applyContent(request, form);
        form.setWorkspaceId(1L);
        form.setSenderManagerId(10L);
        JsonNode response = mapper.readTree(mapper.writeValueAsString(formMapper.toDetails(form)));
        assertEquals(1L, response.get("workspaceId").asLong());
        assertEquals(10L, response.get("senderManagerId").asLong());
        assertEquals(20L, response.get("managerId").asLong());
        assertEquals(6, response.get("nrCode").asInt());
        assertEquals("2026-10-30", response.get("dueDate").asString());
        assertEquals(1L, response.get("questions").get(0).get("id").asLong());
        assertEquals("Queda", response.get("questions").get(0).get("options").get(0).get("text").asString());
        assertEquals(3, response.get("questions").get(1).get("maxFiles").asInt());
        assertTrue(response.get("questions").get(0).get("answer").isArray());
        assertFalse(response.has("id_gestor"));
        assertFalse(response.has("perguntas"));
        assertFalse(response.get("questions").get(0).has("id_pergunta"));
    }

    @Test void englishEnumsKeepTheExistingJsonValues() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertEquals("\"RASCUNHO\"", mapper.writeValueAsString(FormStatus.DRAFT));
        assertEquals(FormStatus.PENDING, mapper.readValue("\"PENDENTE\"", FormStatus.class));
        assertEquals(FormStatus.COMPLETED, mapper.readValue("\"CONCLUIDO\"", FormStatus.class));
        assertEquals("\"alternativa\"", mapper.writeValueAsString(FormQuestionType.OPTION));
        assertEquals(FormQuestionType.TEXT, mapper.readValue("\"texto\"", FormQuestionType.class));
        assertThrows(IllegalArgumentException.class, () -> FormStatus.fromValue("DRAFT"));
        assertThrows(IllegalArgumentException.class, () -> FormQuestionType.fromValue("TEXT"));
    }

    @Test void everyRequestConstraintReferencesAnExistingPortugueseMessage() throws Exception {
        Properties messages = new Properties();
        try (InputStream source = getClass().getResourceAsStream("/ValidationMessages.properties")) {
            assertNotNull(source);
            messages.load(new InputStreamReader(source, StandardCharsets.UTF_8));
        }
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (Class<?> type : new Class<?>[]{FormRequest.class, FormQuestionDefinitionRequest.class, FormOptionRequest.class}) {
                for (PropertyDescriptor property : factory.getValidator().getConstraintsForClass(type).getConstrainedProperties()) {
                    assertMessages(property, messages);
                }
            }
        }
    }

    private void assertMessages(ElementDescriptor element, Properties messages) {
        for (ConstraintDescriptor<?> constraint : element.getConstraintDescriptors()) {
            String template = constraint.getMessageTemplate();
            assertTrue(template.matches("\\{form\\.[A-Za-z.]+}"), template);
            String key = template.substring(1, template.length() - 1);
            String message = messages.getProperty(key);
            assertNotNull(message, key);
            assertFalse(message.isBlank(), key);
            assertFalse(message.contains("\uFFFD"), key);
        }
        if (element instanceof ContainerDescriptor container) {
            for (ContainerElementTypeDescriptor nested : container.getConstrainedContainerElementTypes()) assertMessages(nested, messages);
        }
    }
}
