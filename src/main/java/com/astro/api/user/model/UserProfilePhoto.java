package com.astro.api.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario_foto_perfil")
public class UserProfilePhoto {

    @Id
    @Column(name = "usuario_id")
    private Long userId;

    @Column(name = "caminho_objeto", length = 2048)
    private String objectPath;
}
