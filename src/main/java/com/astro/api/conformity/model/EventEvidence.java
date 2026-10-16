package com.astro.api.conformity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evidencia", uniqueConstraints =
        @UniqueConstraint(columnNames = "caminho_objeto"))
@Getter
@Setter
@NoArgsConstructor
public class EventEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evidencia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conclusao_evento_id", nullable = false)
    private EventConclusion conclusion;

    @Column(name = "nome_original", nullable = false)
    private String originalName;

    @Column(name = "caminho_objeto", nullable = false, length = 2048)
    private String objectPath;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "tamanho_byte", nullable = false)
    private Long sizeBytes;
}
