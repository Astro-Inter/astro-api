package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.Conformity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ConformityRepository extends JpaRepository<Conformity, Long> {
    @Query(value = """
            SELECT v.id_conformidade AS id, v.usuario_id AS "collaboratorId",
                   v.nome AS "collaboratorName", v.email AS "collaboratorEmail",
                   v.nr_id AS "nrId", v.titulo_nr AS "nrTitle", v.data_validade AS "expiresAt",
                   v.origem AS origin, v.aplicavel AS applicable,
                   v.conclusao_evento_id AS "eventCompletionId", v.status
            FROM vw_conformidades v
            JOIN usuario u ON u.id_usuario = v.usuario_id AND u.tipo = 'COLABORADOR'
            WHERE v.workspace_id = :workspaceId AND v.aplicavel = TRUE
              AND v.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            ORDER BY LOWER(v.nome), v.usuario_id, v.nr_id, v.id_conformidade
            """, nativeQuery = true)
    List<ConformityProjection> findAllInScope(@Param("firebaseUid") String firebaseUid,
                                             @Param("workspaceId") Long workspaceId);
}
