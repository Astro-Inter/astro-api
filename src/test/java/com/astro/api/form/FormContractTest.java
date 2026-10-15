package com.astro.api.form;

import com.astro.api.form.dto.request.FormOptionRequest;
import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.dto.request.FormWriteRequest;
import com.astro.api.form.model.FormQuestionType;
import com.astro.api.form.model.FormStatus;
import jakarta.validation.Validation;
import jakarta.validation.metadata.ContainerDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class FormContractTest {
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
        try (var source = getClass().getResourceAsStream("/ValidationMessages.properties")) {
            assertNotNull(source);
            messages.load(new InputStreamReader(source, StandardCharsets.UTF_8));
        }
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Class<?> type : new Class<?>[]{FormWriteRequest.class, FormQuestionRequest.class, FormOptionRequest.class}) {
                for (var property : factory.getValidator().getConstraintsForClass(type).getConstrainedProperties()) {
                    assertMessages(property, messages);
                }
            }
        }
    }

    private void assertMessages(ElementDescriptor element, Properties messages) {
        for (var constraint : element.getConstraintDescriptors()) {
            String template = constraint.getMessageTemplate();
            assertTrue(template.matches("\\{form\\.[A-Za-z.]+}"), template);
            String key = template.substring(1, template.length() - 1);
            String message = messages.getProperty(key);
            assertNotNull(message, key);
            assertFalse(message.isBlank(), key);
            assertFalse(message.contains("\uFFFD"), key);
        }
        if (element instanceof ContainerDescriptor container) {
            for (var nested : container.getConstrainedContainerElementTypes()) assertMessages(nested, messages);
        }
    }
}
