package com.astro.api.role.repository;

import com.astro.api.role.model.Role;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    @Query(value = """
            SELECT id_cargo AS "roleId", id_workspace AS "workspaceId", cargo AS "name",
                   quantidade_colaboradores AS "collaboratorCount", status
            FROM fn_seleciona_cargos(:firebaseUid)
            """, nativeQuery = true)
    List<RoleProjection> findAllByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    @Query(value = """
            SELECT codigo_nr AS "nrCode", descricao AS "description"
            FROM fn_seleciona_cargo_nrs(:firebaseUid, :roleId)
            """, nativeQuery = true)
    List<RoleNrProjection> findNrs(@Param("firebaseUid") String firebaseUid, @Param("roleId") Long roleId);

    @Modifying
    @Query(value = "CALL pr_inseri_cargo(:firebaseUid, :name, :status)", nativeQuery = true)
    void create(@Param("firebaseUid") String firebaseUid, @Param("name") String name, @Param("status") String status);

    @Query(value = "SELECT fn_altera_status_cargo(:firebaseUid, :roleId)", nativeQuery = true)
    Boolean toggleStatus(@Param("firebaseUid") String firebaseUid, @Param("roleId") Long roleId);

    @Query(value = """
            SELECT id_cargo AS "roleId", id_workspace AS "workspaceId", cargo AS "name",
                   quantidade_colaboradores AS "collaboratorCount", status
            FROM fn_altera_cargo(:firebaseUid, :roleId, :name, :status)
            """, nativeQuery = true)
    List<RoleProjection> update(@Param("firebaseUid") String firebaseUid, @Param("roleId") Long roleId,
                                @Param("name") String name, @Param("status") String status);

    @Modifying
    @Query(value = "CALL pr_alterar_cargo_nrs(:firebaseUid, :roleId, CAST(:nrsJson AS JSONB))", nativeQuery = true)
    void updateNrs(@Param("firebaseUid") String firebaseUid, @Param("roleId") Long roleId,
                   @Param("nrsJson") String nrsJson);

    @Query("select c from Role c where c.workspace = :workspace and lower(c.name) in :names")
    List<Role> findByWorkspaceAndNormalizedNames(@Param("workspace") Workspace workspace,
                                                   @Param("names") Collection<String> names);

    @Query(value = "select cargo_id from cargo_nr where nr_id = :nrId and cargo_id in (:roleIds)", nativeQuery = true)
    List<Long> findIdsAssociatedWithNr(@Param("nrId") Integer nrId, @Param("roleIds") Collection<Long> roleIds);
}
