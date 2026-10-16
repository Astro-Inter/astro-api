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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "conclusao_evento")
@Getter
@Setter
@NoArgsConstructor
public class EventCompletion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conclusao_evento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_funcionario_id", nullable = false, unique = true)
    private EventClassParticipant participation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventCompletionStatus status;

    @Column(name = "data_conclusao")
    private LocalDateTime completionDate;

    @Column(name = "data_validacao")
    private LocalDateTime validationDate;

    @Column(name = "data_validade")
    private LocalDate expirationDate;

    @Column(name = "motivo_rejeicao")
    private String rejectionReason;
}
