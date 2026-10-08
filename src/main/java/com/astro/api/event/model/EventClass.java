package com.astro.api.event.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "turma")
@Getter
@Setter
@NoArgsConstructor
public class EventClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turma")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Event event;

    @Column(name = "nome", nullable = false)
    private String name;

    @Column(name = "data_inicial", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "data_termino", nullable = false)
    private LocalDateTime endDate;
}
