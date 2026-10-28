package com.astro.api.conformity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "conformidade")
@Getter
@Setter
@NoArgsConstructor
public class Conformity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conformidade")
    private Long id;
    @Column(name = "tipo", nullable = false)
    private String type = "COLABORADOR";
    @Column(name = "unidade_id")
    private Long unitId;
    @Column(name = "formulario_resposta_id")
    private String formResponseId;
    @Column(name = "usuario_id")
    private Long collaboratorId;
    @Column(name = "nr_id", nullable = false)
    private Integer nrId;
    @Column(name = "aplicavel", nullable = false)
    private boolean applicable;
    @Column(name = "data_validade")
    private LocalDate expiresAt;
    @Column(name = "origem", nullable = false)
    private String origin;
    @Column(name = "conclusao_evento_id")
    private Long eventCompletionId;
}
