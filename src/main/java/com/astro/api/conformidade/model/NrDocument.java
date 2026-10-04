package com.astro.api.conformidade.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "nrs")
@Getter
@Setter
@NoArgsConstructor
public class NrDocument {

    @Id
    private Integer id;

    @Field("nome")
    private String name;

    @Field("tempo_reciclagem_meses")
    private Integer refresherFrequencyMonths;

    @Field("descricao")
    private String description;

    @Field("objetivo")
    private String objective;

    @Field("aplicabilidade")
    private String applicability;

    @Field("usabilidade")
    private String usability;

    @Field("data_criacao")
    private String createdAt;

    @Field("ultima_atualizacao")
    private String updatedAt;

    @Field("revogada")
    private boolean revoked;

}
