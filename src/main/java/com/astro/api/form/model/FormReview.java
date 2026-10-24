package com.astro.api.form.model;

import com.astro.api.form.mapper.FormNumberConverter;
import com.astro.api.form.mapper.FormReviewStatusConverter;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

public record FormReview(
        @Field("status") @ValueConverter(FormReviewStatusConverter.class) FormReviewStatus status,
        @Field("id_gestor_revisor") @ValueConverter(FormNumberConverter.class) Long reviewerManagerId,
        @Field("analisado_em") Instant reviewedAt,
        @Field(value = "descricao", write = Field.Write.NON_NULL) String description) {
    public FormReview {
        if (status != FormReviewStatus.REJECTED && description != null) {
            throw new IllegalArgumentException("A descrição da análise só existe na reprovação");
        }
    }
}
