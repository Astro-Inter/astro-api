package com.astro.api.workspace.model;

import jakarta.persistence.*;

@Entity
@Table(name = "workspace")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_workspace")
    public Long id;

    @Column(name = "nome", nullable = false)
    public String name;

    @Column(name = "cnpj", length = 14, nullable = false)
    public String cnpj;
}
