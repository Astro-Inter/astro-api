package com.astro.api.role.service;

import com.astro.api.conformity.model.NrDocument;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleConfigurationTest {
    @Test
    void resolvesTheMongoCollectionDefaultAndOverride() {
        for (String collection : new String[]{"nrs", "NRs", "nr_catalogo"}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                if (!"nrs".equals(collection)) {
                    context.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("test", Map.of("astro.mongodb.nr-collection", collection)));
                }
                context.registerBean(MongoMappingContext.class);
                context.refresh();
                var entity = context.getBean(MongoMappingContext.class).getRequiredPersistentEntity(NrDocument.class);
                assertEquals(collection, entity.getCollection());
            }
        }
    }
}
