package com.astro.api.user.model;

import com.astro.api.cargo.model.Cargo;
import com.astro.api.unit.model.Unit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class User extends Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    @Column(name = "cpf", unique = true)
    private String cpf;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private UserType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade")
    private WorkModel workModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private UserStatus status;

    @Column(name = "criado_em")
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "unidade_id")
    private Unit unit;

    @ManyToOne
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;
}
