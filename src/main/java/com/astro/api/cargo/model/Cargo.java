package com.astro.api.cargo.model;

import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "cargo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "nome"})
)
@Getter
@Setter
@NoArgsConstructor
public class Cargo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cargo")
    private Long id;

    @Column(name = "nome", nullable = false)
    private String name;

    @Column(name = "ativo")
    private boolean active;

    @ManyToOne
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;
}
