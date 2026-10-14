package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.Conformity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConformityRepository extends JpaRepository<Conformity, Long> {
    @Query(value = """
            SELECT c.* FROM conformidade c
            JOIN usuario u ON u.id_usuario = c.usuario_id AND u.tipo = 'COLABORADOR'
            JOIN unidade un ON un.id_unidade = u.unidade_id
            WHERE c.id_conformidade = :conformityId AND un.workspace_id = :workspaceId
              AND u.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            """, nativeQuery = true)
    Optional<Conformity> findRecordInScope(@Param("conformityId") Long conformityId,
            @Param("firebaseUid") String firebaseUid, @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM conclusao_evento ce
            JOIN turma_funcionario tf ON tf.id_turma_funcionario = ce.turma_funcionario_id
            JOIN turma t ON t.id_turma = tf.turma_id
            JOIN evento e ON e.id_evento = t.evento_id
            WHERE ce.id_conclusao_evento = :eventCompletionId
              AND tf.usuario_id = :collaboratorId AND e.nr_id = :nrId)
            """, nativeQuery = true)
    boolean completionMatchesNr(@Param("eventCompletionId") Long eventCompletionId,
            @Param("collaboratorId") Long collaboratorId, @Param("nrId") Integer nrId);

    // Serializes check-and-write against all PostgreSQL DML, including writers outside this API.
    // Readers remain concurrent. Acquire before locking collaborators or conformities.
    @Modifying
    @Query(value = "LOCK TABLE conformidade IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockWrites();

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM conformidade WHERE usuario_id = :collaboratorId
                AND nr_id = :nrId AND aplicavel = TRUE
                AND (:excludedId IS NULL OR id_conformidade <> :excludedId))
            """, nativeQuery = true)
    boolean hasApplicableConflict(@Param("collaboratorId") Long collaboratorId, @Param("nrId") Integer nrId,
                                  @Param("excludedId") Long excludedId);

    String DETAILS = """
            SELECT c.id_conformidade AS id, c.usuario_id AS "collaboratorId",
                   u.nome AS "collaboratorName", u.email AS "collaboratorEmail",
                   c.nr_id AS "nrId", nr.titulo AS "nrTitle", c.data_validade AS "expiresAt",
                   c.origem AS origin, c.aplicavel AS applicable,
                   c.conclusao_evento_id AS "eventCompletionId",
                   CASE WHEN c.data_validade IS NULL THEN 'Sem validade'
                        WHEN c.data_validade < CURRENT_DATE THEN 'Vencida' ELSE 'Válida' END AS status
            FROM conformidade c
            JOIN usuario u ON u.id_usuario = c.usuario_id AND u.tipo = 'COLABORADOR'
            JOIN unidade un ON un.id_unidade = u.unidade_id
            JOIN nr_catalogo nr ON nr.codigo_nr = c.nr_id
            WHERE c.id_conformidade = :conformityId AND un.workspace_id = :workspaceId
              AND u.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            """;

    @Query(value = DETAILS, nativeQuery = true)
    Optional<ConformityProjection> findDetailsInScope(@Param("conformityId") Long conformityId,
            @Param("firebaseUid") String firebaseUid, @Param("workspaceId") Long workspaceId);

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
