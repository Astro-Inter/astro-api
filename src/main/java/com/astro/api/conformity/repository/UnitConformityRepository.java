package com.astro.api.conformity.repository;

import com.astro.api.conformity.model.Conformity;
import com.astro.api.unit.model.Unit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitConformityRepository extends JpaRepository<Conformity, Long> {
    @Query(value = """
            SELECT un.* FROM unidade un WHERE un.id_unidade = :unitId AND un.workspace_id = :workspaceId
              AND un.id_unidade IN (SELECT id_unidade FROM fn_seleciona_unidades(:uid)) FOR UPDATE
            """, nativeQuery = true)
    Optional<Unit> findUnitInScope(@Param("unitId") Long unitId, @Param("workspaceId") Long workspaceId,
                                 @Param("uid") String uid);

    @Query(value = """
            SELECT c.* FROM conformidade c JOIN unidade un ON un.id_unidade = c.unidade_id
            WHERE c.id_conformidade = :id AND c.tipo = 'UNIDADE' AND un.workspace_id = :workspaceId
              AND un.id_unidade IN (SELECT id_unidade FROM fn_seleciona_unidades(:uid))
            """, nativeQuery = true)
    Optional<Conformity> findRecordInScope(@Param("id") Long id, @Param("workspaceId") Long workspaceId,
                                         @Param("uid") String uid);

    @Query("SELECT COUNT(c) > 0 FROM Conformity c WHERE c.type = 'UNIDADE' AND c.unitId = :unitId AND c.nrId = :nrId AND c.applicable = true AND (:excludedId IS NULL OR c.id <> :excludedId)")
    boolean hasApplicableConflict(@Param("unitId") Long unitId, @Param("nrId") Integer nrId, @Param("excludedId") Long excludedId);

    Optional<Conformity> findByFormResponseId(String formResponseId);

    @Query(value = """
            SELECT v.id_conformidade AS id, v.unidade_id AS "unitId", v.nome_unidade AS "unitName",
                   v.nr_id AS "nrId", v.titulo_nr AS "nrTitle", v.data_validade AS "expiresAt",
                   v.origem AS origin, v.aplicavel AS applicable, v.formulario_resposta_id AS "formResponseId", v.status
            FROM vw_conformidade_unidade v WHERE v.workspace_id = :workspaceId AND v.aplicavel = TRUE
              AND v.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:uid))
            ORDER BY LOWER(v.nome_unidade), v.unidade_id, v.nr_id, v.id_conformidade
            """, nativeQuery = true)
    List<UnitConformityProjection> findAllInScope(@Param("workspaceId") Long workspaceId, @Param("uid") String uid);

    @Query(value = """
            SELECT c.id_conformidade AS id, c.unidade_id AS "unitId", un.nome AS "unitName",
                   c.nr_id AS "nrId", nr.titulo AS "nrTitle", c.data_validade AS "expiresAt",
                   c.origem AS origin, c.aplicavel AS applicable, c.formulario_resposta_id AS "formResponseId",
                   CASE WHEN c.data_validade IS NULL THEN 'Sem validade'
                        WHEN c.data_validade < CURRENT_DATE THEN 'Vencida' ELSE 'Válida' END AS status
            FROM conformidade c JOIN unidade un ON un.id_unidade = c.unidade_id
            JOIN nr_catalogo nr ON nr.codigo_nr = c.nr_id
            WHERE c.id_conformidade = :id AND c.tipo = 'UNIDADE' AND un.workspace_id = :workspaceId
              AND un.id_unidade IN (SELECT id_unidade FROM fn_seleciona_unidades(:uid))
            """, nativeQuery = true)
    Optional<UnitConformityProjection> findDetailsInScope(@Param("id") Long id, @Param("workspaceId") Long workspaceId,
                                                        @Param("uid") String uid);
}
