package com.astro.api.nrreview.mapper;

import com.astro.api.nrreview.dto.NrApprovalResult;
import com.astro.api.nrreview.dto.NrContent;
import com.astro.api.nrreview.dto.NrSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class NrReviewMapper {
    private static final List<String> DOCUMENT_FIELDS = List.of(
            "_id", "nome", "descricao", "objetivo", "aplicabilidade", "usabilidade",
            "revogada", "tempo_reciclagem_meses", "ultima_atualizacao", "data_criacao");

    public NrSnapshot toSnapshot(Document document, Document approved) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : DOCUMENT_FIELDS) {
            fields.put(field, document.get(field));
        }
        String revision = revision(document);
        String approvedRevision = revision(approved);
        return new NrSnapshot(fields, revision, approvedRevision);
    }

    public NrApprovalResult toApprovalResult(NrSnapshot snapshot, boolean sqlSynchronized) {
        return new NrApprovalResult(snapshot.document(), snapshot.revision(), snapshot.approvedRevision(), sqlSynchronized);
    }

    public Document applyContent(Document original, NrContent content) {
        return new Document(original)
                .append("nome", content.nome())
                .append("descricao", content.descricao())
                .append("objetivo", content.objetivo())
                .append("aplicabilidade", content.aplicabilidade())
                .append("usabilidade", content.usabilidade())
                .append("revogada", content.revogada())
                .append("tempo_reciclagem_meses", content.tempo_reciclagem_meses());
    }

    public String toSqlPayload(int nrId, Document approved) {
        Document sqlDocument = new Document("id", nrId)
                .append("nome", approved.getString("nome"))
                .append("revogada", approved.getBoolean("revogada"))
                .append("tempo_reciclagem_mes", approved.get("tempo_reciclagem_meses"));
        return "[" + sqlDocument.toJson() + "]";
    }

    public String revision(Document document) {
        if (document == null) {
            return "absent";
        }
        Document canonicalDocument = new Document(canonical(document));
        byte[] snapshot = canonicalDocument.toJson().getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(snapshot);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível", exception);
        }
    }

    private Map<String, Object> canonical(Map<?, ?> document) {
        Map<String, Object> sorted = new TreeMap<>();
        document.forEach((key, value) -> sorted.put(key.toString(), canonicalValue(value)));
        return sorted;
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return new Document(canonical(nested));
        }
        if (value instanceof List<?> items) {
            return items.stream().map(this::canonicalValue).toList();
        }
        return value;
    }
}
