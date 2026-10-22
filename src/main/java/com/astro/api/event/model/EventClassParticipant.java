package com.astro.api.event.model;

import com.astro.api.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "turma_colaborador", uniqueConstraints = @UniqueConstraint(columnNames = {"turma_id", "usuario_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EventClassParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turma_colaborador")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id", nullable = false)
    private EventClass eventClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;
}
