package com.astro.api.form;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.FormAppearanceRequest;
import com.astro.api.form.dto.request.FormRequest;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormAppearance;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.strategy.FormValidationMode;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class FormAppearanceContractTest {
    @Test void mapsAppearanceThroughRequestStorageDetailAndSummaryWithoutChangingTitle() {
        JsonMapper json = JsonMapper.builder().build();
        FormRequest request = json.readValue("""
                {"title":"Inspeção","status":"RASCUNHO","questions":[],
                 "appearance":{"icon":"calendar","color":"orange"}}
                """, FormRequest.class);
        Form form = new Form();
        FormMapper mapper = new FormMapper();
        mapper.applyContent(request, form);
        assertEquals("Inspeção", form.getTitle());
        assertEquals(new FormAppearance("calendar", "orange"), form.getAppearance());
        assertEquals(request.appearance(), mapper.toFormRequest(form).appearance());
        assertEquals("calendar", mapper.toDetails(form).appearance().icon());
        assertEquals("orange", mapper.toSummary(form).appearance().color());
        mapper.applyContent(new FormRequest("Novo nome", "", null, null, null, null,
                FormStatus.DRAFT, List.of()), form);
        assertEquals(new FormAppearance("calendar", "orange"), form.getAppearance());
        mapper.applyContent(new FormRequest("Novo nome", "", null, null, null, null,
                FormStatus.DRAFT, List.of(), new FormAppearanceRequest("clock", "blue")), form);
        assertEquals(new FormAppearance("clock", "blue"), form.getAppearance());
    }

    @Test void mongoFieldsArePortugueseAndRoundTripWithReferencesAndNestedQuestions() throws Exception {
        MongoMappingContext context = new MongoMappingContext();
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> {});
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        Form form = FormTestFixtures.form(1, 10, 20, 30, FormStatus.DRAFT);
        form.setAppearance(new FormAppearance("security", "teal"));
        Document document = new Document();
        converter.write(form, document);
        assertEquals("formularios", context.getRequiredPersistentEntity(Form.class).getCollection());
        assertEquals(10, document.get("workspace_id", Number.class).intValue());
        assertEquals(20, document.get("gestor_remetente_id", Number.class).intValue());
        assertEquals(30, document.get("gestor_id", Number.class).intValue());
        Document appearance = document.get("configuracao", Document.class);
        assertEquals("security", appearance.getString("icone"));
        assertEquals("teal", appearance.getString("cor"));
        assertFalse(document.containsKey("appearance"));
        assertFalse(document.containsKey("id_workspace"));
        assertTrue(document.getList("perguntas", Document.class).getFirst().containsKey("pergunta_id"));
        Form restored = converter.read(Form.class, document);
        assertEquals(form.getAppearance(), restored.getAppearance());
        assertEquals(form.getWorkspaceId(), restored.getWorkspaceId());
    }

    @Test void rejectsUnknownOrPartialAppearanceThroughBeanAndServiceValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (FormAppearanceRequest appearance : List.of(new FormAppearanceRequest("script", "orange"),
                    new FormAppearanceRequest("calendar", "invalid"), new FormAppearanceRequest(null, "blue"))) {
                FormRequest request = new FormRequest("", "", null, null, null, null,
                        FormStatus.DRAFT, List.of(), appearance);
                assertFalse(factory.getValidator().validate(request).isEmpty());
                assertThrows(BusinessException.class,
                        () -> FormTestFixtures.validator().validate(request, FormValidationMode.DRAFT));
            }
        }
    }
}
