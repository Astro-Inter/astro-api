package com.astro.api.conformity.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class ConformityApplicabilityRequestDeserializer extends ValueDeserializer<ConformityApplicabilityRequest> {
    @Override
    public ConformityApplicabilityRequest deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode body = parser.readValueAsTree();
        if (!body.isObject()) return context.reportInputMismatch(ConformityApplicabilityRequest.class, "O corpo deve ser um objeto JSON");
        JsonNode applicable = body.get("applicable");
        if (applicable != null && !applicable.isNull() && !applicable.isBoolean()) {
            return context.reportInputMismatch(ConformityApplicabilityRequest.class, "O campo applicable deve ser booleano");
        }
        return new ConformityApplicabilityRequest(applicable == null || applicable.isNull() ? null : applicable.booleanValue());
    }
}
