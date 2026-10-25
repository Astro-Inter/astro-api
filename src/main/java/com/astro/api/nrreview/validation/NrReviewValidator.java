package com.astro.api.nrreview.validation;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.nrreview.mapper.NrReviewMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class NrReviewValidator {
    private static final DateTimeFormatter SOURCE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private final NrReviewMapper nrReviewMapper;

    public NrReviewValidator(NrReviewMapper nrReviewMapper) {
        this.nrReviewMapper = nrReviewMapper;
    }

    public void validateId(int nrId) {
        if (nrId <= 0) {
            throw new BusinessException("Número de NR inválido.");
        }
    }

    public Document requireDocument(Document document) {
        if (document == null) {
            throw new ResourceNotFoundException("NR não encontrada.");
        }
        return document;
    }

    public void validateRevision(Document document, String expectedRevision) {
        String currentRevision = nrReviewMapper.revision(document);
        if (!currentRevision.equals(expectedRevision)) {
            throw new ConflictException("A versão da NR mudou. Reabra a revisão para conferir os dados atuais.");
        }
    }

    public void validateDocument(Document document) {
        for (String field : List.of("nome", "descricao", "objetivo", "aplicabilidade")) {
            Object value = document.get(field);
            int maximumLength = field.equals("nome") ? 300 : 20000;
            if (!(value instanceof String text) || text.isBlank() || text.length() > maximumLength) {
                throw new BusinessException("Confira o campo " + field + " antes de aprovar.");
            }
        }
        if (!(document.get("usabilidade") instanceof String usability)
                || !Set.of("Colaborador", "Empresa").contains(usability)
                || !(document.get("revogada") instanceof Boolean)
                || !(document.get("tempo_reciclagem_meses") instanceof Number months)
                || months.doubleValue() != months.intValue() || months.intValue() <= 0) {
            throw new BusinessException("Confira usabilidade, revogação e tempo de reciclagem.");
        }
        try {
            LocalDate.parse(document.getString("ultima_atualizacao"), SOURCE_DATE_FORMAT);
        } catch (RuntimeException exception) {
            throw new BusinessException("A data da fonte deve ser válida no formato DD/MM/AAAA.");
        }
    }
}
