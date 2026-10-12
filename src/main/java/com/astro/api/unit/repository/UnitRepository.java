package com.astro.api.unit.repository;

import com.astro.api.unit.model.Unit;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByWorkspaceAndNameIn(Workspace workspace, Collection<String> names);

    @Query(value = """
            SELECT id_unidade AS "unitId", id_workspace AS "workspaceId", nome AS "name",
                   status, quantidade_colaboradores AS "collaboratorCount", cep AS "postalCode",
                   rua AS "street", cidade AS "city", bairro AS "neighborhood", estado AS "state",
                   complemento AS "addressLine2"
            FROM fn_seleciona_unidades(:firebaseUid)
            """, nativeQuery = true)
    List<UnitProjection> findAllByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    @Query(value = "SELECT fn_altera_status_unidade(:firebaseUid, :unitId)", nativeQuery = true)
    Boolean toggleStatus(@Param("firebaseUid") String firebaseUid, @Param("unitId") Long unitId);

    @Query(value = """
            SELECT codigo_nr AS "nrCode", descricao AS "description"
            FROM fn_seleciona_unidade_nrs(:firebaseUid, :unitId)
            """, nativeQuery = true)
    List<UnitNrProjection> findNrs(@Param("firebaseUid") String firebaseUid, @Param("unitId") Long unitId);

    @Modifying
    @Query(value = "CALL pr_alterar_unidade_nrs(:firebaseUid, :unitId, CAST(:nrsJson AS JSONB))", nativeQuery = true)
    void updateNrs(@Param("firebaseUid") String firebaseUid, @Param("unitId") Long unitId,
                   @Param("nrsJson") String nrsJson);
}
