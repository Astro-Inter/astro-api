package com.astro.api.conformity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "nr_catalogo")
@Getter
public class NrCatalog {
    @Id
    @Column(name = "codigo_nr")
    private int codeNr;

    @Column(name = "titulo", nullable = false)
    private String title;

    @Column(name = "tempo_reciclagem_mes", nullable = false)
    private int refresherFrequency;

    @Column(name = "revogada")
    private boolean revoked;
}
