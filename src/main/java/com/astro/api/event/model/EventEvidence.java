package com.astro.api.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evidencia")
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
    private EventCompletion completion;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String originalName;

    @Column(name = "caminho_objeto", nullable = false, unique = true, length = 2048)
    private String objectPath;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "tamanho_byte", nullable = false)
    private long sizeBytes;
}
