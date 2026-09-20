package com.astro.api.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Audited;
import jakarta.persistence.Id;

@Getter
@Setter
@Entity
@Audited.Table(name = "conta")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome")
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;
}
