package com.astro.api.form;

import com.astro.api.form.model.Form;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.junit.jupiter.api.Assertions.*;

class FormNumberMappingTest {
    private MappingMongoConverter converter() throws Exception {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(MongoCustomConversions.create(adapter -> {}).getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(Form.class));
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.afterPropertiesSet();
        return converter;
    }

    @Test void newFormsStoreWorkspaceAndSenderAsInt32() throws Exception {
        Form form = new Form();
        form.setId(UUID.randomUUID().toString());
        form.setWorkspaceId(1L);
        form.setSenderManagerId(5L);
        Document storedForm = new Document();
        converter().write(form, storedForm);
        assertEquals(1, assertInstanceOf(Integer.class, storedForm.get("id_workspace")));
        assertEquals(5, assertInstanceOf(Integer.class, storedForm.get("id_gestor_remetente")));
    }

    @Test void legacyLongValuesRemainReadableAndAreNormalizedOnFullWrite() throws Exception {
        Document legacyForm = new Document("_id", UUID.randomUUID().toString())
                .append("id_workspace", 1L).append("id_gestor_remetente", 5L).append("id_gestor", 3);
        MappingMongoConverter converter = converter();
        Form form = converter.read(Form.class, legacyForm);
        assertEquals(1L, form.getWorkspaceId());
        assertEquals(5L, form.getSenderManagerId());
        assertEquals(3L, form.getManagerId());
        Document storedForm = new Document();
        converter.write(form, storedForm);
        assertInstanceOf(Integer.class, storedForm.get("id_workspace"));
        assertInstanceOf(Integer.class, storedForm.get("id_gestor_remetente"));
    }

    @Test void largeIdentifiersRetainTheirExactValue() throws Exception {
        Form form = new Form();
        form.setWorkspaceId(Long.MAX_VALUE);
        form.setSenderManagerId(Long.MAX_VALUE);
        Document storedForm = new Document();
        converter().write(form, storedForm);
        assertEquals(Long.MAX_VALUE, assertInstanceOf(Long.class, storedForm.get("id_workspace")));
        assertEquals(Long.MAX_VALUE, assertInstanceOf(Long.class, storedForm.get("id_gestor_remetente")));
    }
}
