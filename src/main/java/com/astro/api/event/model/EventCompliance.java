package com.astro.api.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "conformidade", uniqueConstraints =
        @UniqueConstraint(columnNames = "conclusao_evento_id"))
@Getter
@Setter
@NoArgsConstructor
public class EventCompliance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conformidade")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long userId;

    @Column(name = "nr_id", nullable = false)
    private Integer nrId;

    @Column(name = "aplicavel", nullable = false)
    private Boolean applicable;

    @Column(name = "data_validade")
    private LocalDate validityDate;

    @Column(name = "origem", nullable = false)
    private String origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conclusao_evento_id")
    private EventConclusion conclusion;
}
