package com.astro.api.form.mapper;

import com.astro.api.form.dto.request.FormResponseRequest;
import com.astro.api.form.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FormAnswerMapper {
    public void applyAnswers(Form form, FormResponseRequest request, Map<Long, List<Object>> answers,
                             Map<Integer, FormImage> images) {
        for (Form.FormQuestion question : form.getQuestions()) {
            List<Object> normalized = answers.get(question.getId());
            if (FormQuestionType.PHOTO.value().equals(question.getType())) {
                question.setAnswer(normalized.stream().map(index -> (Object) toStoredImage(images.get((Integer) index))).toList());
            } else {
                question.setAnswer(normalized.stream().map(this::toStoredAnswer).toList());
            }
        }
        form.setLocationConsent(request.locationConsent());
        form.setLocations(request.locations().stream().map(location -> new FormLocationReading(location.latitude(),
                location.longitude(), location.accuracyMeters(), location.recordedAt())).toList());
    }

    private Object toStoredAnswer(Object value) {
        if (value instanceof Long number) return storedNumber(number);
        if (value instanceof FormOtherAnswer other) return Map.of("outro_texto", other.text());
        return value;
    }

    private Number storedNumber(long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int) value;
        return value;
    }

    public Map<String, Object> toStoredImage(FormImage image) {
        return Map.of("id_imagem", image.id(), "chave_objeto", image.objectKey(), "nome", image.name(),
                "tipo_conteudo", image.contentType(), "tamanho_bytes", storedNumber(image.sizeBytes()));
    }

    public List<FormImage> images(Form form) {
        if (form.getQuestions() == null) return List.of();
        List<FormImage> images = new ArrayList<>();
        for (Form.FormQuestion question : form.getQuestions()) {
            if (!FormQuestionType.PHOTO.value().equals(question.getType()) || question.getAnswer() == null) continue;
            for (Object value : question.getAnswer()) {
                if (value instanceof Map<?, ?> image && image.get("id_imagem") instanceof String imageId
                        && image.get("chave_objeto") instanceof String objectKey
                        && image.get("nome") instanceof String name && image.get("tipo_conteudo") instanceof String contentType
                        && image.get("tamanho_bytes") instanceof Number size) {
                    images.add(new FormImage(imageId, objectKey, name, contentType, size.longValue()));
                }
            }
        }
        return List.copyOf(images);
    }

    public static List<Object> toPublicAnswers(Form.FormQuestion question) {
        if (question.getAnswer() == null) return List.of();
        return question.getAnswer().stream().map(value -> {
            if (value instanceof Map<?, ?> fields) {
                if (FormQuestionType.PHOTO.value().equals(question.getType())) {
                    Map<String, Object> image = new LinkedHashMap<>();
                    copyField(fields, image, "id_imagem", "id");
                    copyField(fields, image, "nome", "name");
                    copyField(fields, image, "tipo_conteudo", "contentType");
                    copyField(fields, image, "tamanho_bytes", "sizeBytes");
                    return (Object) image;
                }
                if (fields.get("outro_texto") instanceof String text) return (Object) Map.of("otherText", text);
            }
            return value;
        }).toList();
    }

    private static void copyField(Map<?, ?> source, Map<String, Object> target, String storedName, String apiName) {
        if (source.get(storedName) != null) target.put(apiName, source.get(storedName));
    }
}
