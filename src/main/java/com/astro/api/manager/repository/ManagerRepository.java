package com.astro.api.manager.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ManagerRepository extends JpaRepository<User, Long> {
    @Query(value = """
            SELECT id_usuario AS id, nome AS name, email,
                   unidade_id AS unitId, unidade AS unitName, status
            FROM vw_gestores
            WHERE workspace_id = :workspaceId
            ORDER BY nome, id_usuario
            """, nativeQuery = true)
    List<ManagerSummaryProjection> findAllInWorkspace(@Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT id_usuario AS id, nome AS name, email, cpf, modalidade AS workModel, status,
                   cargo_id AS roleId, cargo AS roleName, unidade_id AS unitId,
                   unidade AS unitName, workspace_id AS workspaceId
            FROM vw_gestores WHERE id_usuario = :managerId AND workspace_id = :workspaceId
            """, nativeQuery = true)
    Optional<ManagerDetailsProjection> findDetailsInWorkspace(@Param("managerId") Long managerId,
                                                             @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT u.* FROM usuario u JOIN unidade un ON un.id_unidade = u.unidade_id
            WHERE u.id_usuario = :userId AND un.workspace_id = :workspaceId
            FOR UPDATE OF u
            """, nativeQuery = true)
    Optional<User> findUserForUpdateInWorkspace(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    @Query(value = "SELECT ativo FROM cargo WHERE id_cargo = :roleId AND workspace_id = :workspaceId FOR SHARE", nativeQuery = true)
    Boolean lockRoleActive(@Param("roleId") Long roleId, @Param("workspaceId") Long workspaceId);

    @Query(value = "SELECT ativo FROM unidade WHERE id_unidade = :unitId AND workspace_id = :workspaceId FOR SHARE", nativeQuery = true)
    Boolean lockUnitActive(@Param("unitId") Long unitId, @Param("workspaceId") Long workspaceId);
}
