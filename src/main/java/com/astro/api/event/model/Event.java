package com.astro.api.event.model;

import com.astro.api.user.model.User;
import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "evento")
@Getter
@Setter
@NoArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    @Column(name = "titulo", nullable = false)
    private String title;

    @Column(name = "descricao")
    private String description;

    @Column(name = "nr_id")
    private Integer nrId;

    @Column(name = "link_externo")
    private String externalLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_conclusao", nullable = false)
    private CompletionMode completionMode;

    @Column(name = "evidencia_obrigatoria", nullable = false)
    private boolean mandatoryEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestor_id", nullable = false)
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "data_cancelamento")
    private Instant cancellationDate;

    @Column(name = "motivo_cancelamento")
    private String cancellationReason;
}
