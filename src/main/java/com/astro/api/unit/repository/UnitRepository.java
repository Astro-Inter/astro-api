package com.astro.api.unit.repository;

import com.astro.api.unit.model.Unit;
import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByWorkspaceAndNameIn(Workspace workspace, Collection<String> names);
    List<Unit> findByWorkspace(Workspace workspace);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Unit> findByIdAndWorkspace_Id(Long unitId, Long workspaceId);

    @Query(value = """
            SELECT unit.id_unidade AS "unitId", unit.ativo AS "active",
                   (address.geolocalizacao)[1] AS "latitude", (address.geolocalizacao)[0] AS "longitude"
            FROM unidade unit
            LEFT JOIN unidade_endereco address ON address.unidade_id = unit.id_unidade
            WHERE unit.id_unidade = :unitId AND unit.workspace_id = :workspaceId
            """, nativeQuery = true)
    Optional<UnitLocationProjection> findLocationByIdAndWorkspaceId(@Param("unitId") Long unitId,
                                                                  @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT listed.id_unidade AS "unitId", listed.id_workspace AS "workspaceId", listed.nome AS "name",
                   listed.status, listed.quantidade_colaboradores AS "collaboratorCount", listed.cep AS "postalCode",
                   listed.rua AS "street", listed.cidade AS "city", listed.bairro AS "neighborhood", listed.estado AS "state",
                   listed.complemento AS "addressLine2", address.numero AS "number"
            FROM fn_seleciona_unidades(:firebaseUid) listed
            LEFT JOIN unidade_endereco address ON address.unidade_id = listed.id_unidade
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
