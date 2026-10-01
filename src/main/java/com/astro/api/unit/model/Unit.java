package com.astro.api.unit.model;

import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.*;

@Entity
@Table(
        name = "unidade",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "nome"})
)
public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_unidade")
    public Long id;

    @Column(name = "nome", nullable = false)
    public String name;

    @Column(name = "ativo")
    public boolean isActive;

    @ManyToOne
    @JoinColumn(name = "workspace_id")
    public Workspace workspace;

    @OneToOne(mappedBy = "unit")
    public UnitAddress address;
}
