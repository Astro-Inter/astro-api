package com.astro.api.unit.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class UnitNrRequestDeserializer extends ValueDeserializer<UnitNrRequest> {
    @Override
    public UnitNrRequest deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode item = parser.readValueAsTree();
        if (!item.isObject()) {
            return context.reportInputMismatch(UnitNrRequest.class, "Cada NR deve ser um objeto JSON");
        }
        JsonNode code = item.get("nr");
        JsonNode active = item.get("active");
        if (code != null && !code.isNull() && (!code.isIntegralNumber() || !code.canConvertToInt())) {
            return context.reportInputMismatch(UnitNrRequest.class, "O código da NR deve ser um número inteiro");
        }
        if (active != null && !active.isNull() && !active.isBoolean()) {
            return context.reportInputMismatch(UnitNrRequest.class, "O campo active deve ser booleano");
        }
        return new UnitNrRequest(code == null || code.isNull() ? null : code.intValue(),
                active == null || active.isNull() ? null : active.booleanValue());
    }
}
