package com.astro.api.home.mapper;

import com.astro.api.home.dto.response.WorkspaceHomeDatabaseResponse;
import com.astro.api.home.dto.response.WorkspaceHomeResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

@Component
public class WorkspaceHomeMapper {
    private static final Map<String, String> STATISTICS_FIELDS = Map.of(
            "unitCount", "quantidade_unidades",
            "collaboratorCount", "quantidade_colaboradores",
            "requirementCount", "quantidade_requisitos",
            "compliantCount", "quantidade_conformes",
            "expiringSoonCount", "quantidade_proximos_vencimento",
            "expiredCount", "quantidade_vencidos",
            "pendingCount", "quantidade_pendentes",
            "overallCompliancePercentage", "percentual_conformidade_geral",
            "referenceDate", "data_referencia");
    private final JsonMapper jsonMapper;

    public WorkspaceHomeMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public WorkspaceHomeDatabaseResponse fromDatabase(String json) {
        JsonNode home = jsonMapper.readTree(json);
        if (home == null || !home.isObject() || home.get("estatisticas") == null
                || !home.get("estatisticas").isObject()) {
            throw new IllegalStateException("Dados da Home retornados pelo banco são inválidos");
        }
        ObjectNode statistics = jsonMapper.createObjectNode();
        STATISTICS_FIELDS.forEach((field, databaseField) ->
                statistics.set(field, home.get("estatisticas").get(databaseField)));
        ObjectNode normalized = jsonMapper.createObjectNode();
        normalized.set("userName", home.get("nome_usuario"));
        normalized.set("statistics", statistics);
        return jsonMapper.treeToValue(normalized, WorkspaceHomeDatabaseResponse.class);
    }

    public WorkspaceHomeResponse toResponse(WorkspaceHomeDatabaseResponse home, String profilePhotoUrl) {
        return new WorkspaceHomeResponse(home.userName(), home.statistics(), profilePhotoUrl);
    }
}
