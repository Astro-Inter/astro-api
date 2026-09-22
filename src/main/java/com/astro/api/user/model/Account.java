package com.astro.api.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class Account {

    @Column(name = "nome")
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;
}
