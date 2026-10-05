package com.astro.api.conformidade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "nr_catalog")
public class NrCatalog {
    @Id
    private int codeNr;

    @Column(name = "titulo", nullable = false)
    private String title;

    @Column(name = "tempo_reciclagem_mes", nullable = false)
    private int refresherFrequency;

    @Column(name = "revogada")
    private boolean revoked;
}
