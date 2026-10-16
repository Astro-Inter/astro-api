package com.astro.api.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "conclusao_evento", uniqueConstraints =
        @UniqueConstraint(columnNames = "turma_funcionario_id"))
@Getter
@Setter
@NoArgsConstructor
public class EventConclusion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conclusao_evento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_funcionario_id", nullable = false)
    private EventClassParticipant participation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventConclusionStatus status;

    @Column(name = "data_conclusao")
    private LocalDateTime completionDate;

    @Column(name = "data_validacao")
    private LocalDateTime validationDate;

    @Column(name = "data_validade")
    private java.time.LocalDate validityDate;

    @Column(name = "motivo_rejeicao")
    private String rejectionReason;
}
