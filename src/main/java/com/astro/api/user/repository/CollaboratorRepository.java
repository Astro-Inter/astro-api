package com.astro.api.user.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollaboratorRepository extends JpaRepository<User, Long> {
    @Query(value = """
            SELECT u.id_usuario FROM usuario u JOIN unidade un ON un.id_unidade = u.unidade_id
            WHERE u.cpf = :cpf AND u.tipo = 'COLABORADOR' AND un.workspace_id = :workspaceId
              AND u.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            """, nativeQuery = true)
    Optional<Long> findIdByCpfInScope(@Param("cpf") String cpf, @Param("firebaseUid") String firebaseUid,
                                     @Param("workspaceId") Long workspaceId);
    @Query(value = "SELECT ativo FROM cargo WHERE id_cargo = :roleId AND workspace_id = :workspaceId FOR SHARE", nativeQuery = true)
    Boolean lockRoleActive(@Param("roleId") Long roleId, @Param("workspaceId") Long workspaceId);

    @Query(value = "SELECT ativo FROM unidade WHERE id_unidade = :unitId AND workspace_id = :workspaceId FOR SHARE", nativeQuery = true)
    Boolean lockUnitActive(@Param("unitId") Long unitId, @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT u.* FROM usuario u
            INNER JOIN unidade un ON un.id_unidade = u.unidade_id
            WHERE u.id_usuario = :collaboratorId AND u.tipo IN ('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')
              AND un.workspace_id = :workspaceId
              AND u.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            FOR UPDATE OF u
            """, nativeQuery = true)
    Optional<User> findForUpdateInScope(@Param("collaboratorId") Long collaboratorId,
            @Param("firebaseUid") String firebaseUid, @Param("workspaceId") Long workspaceId);

    @Modifying
    @Query("""
            UPDATE User u
            SET u.status = com.astro.api.user.model.UserStatus.DESATIVADO
            WHERE u.id = :collaboratorId AND u.firebaseUid = :firebaseUid
            """)
    int deactivateAfterEmailFailure(@Param("collaboratorId") Long collaboratorId, @Param("firebaseUid") String firebaseUid);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM fn_seleciona_unidades(:firebaseUid) WHERE id_unidade = :unitId)
            """, nativeQuery = true)
    boolean canAccessUnit(@Param("firebaseUid") String firebaseUid, @Param("unitId") Long unitId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = :email AND (:excludedId IS NULL OR u.id <> :excludedId)")
    boolean emailExists(@Param("email") String email, @Param("excludedId") Long excludedId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.cpf = :cpf AND (:excludedId IS NULL OR u.id <> :excludedId)")
    boolean cpfExists(@Param("cpf") String cpf, @Param("excludedId") Long excludedId);

    @Query(value = """
            SELECT v.id_usuario AS id, v.nome AS name, v.email, v.cpf,
                   v.modalidade AS "workModel", v.status, u.tipo AS "userType",
                   v.cargo_id AS "roleId", v.cargo AS "roleName",
                   v.unidade_id AS "unitId", v.unidade AS "unitName", v.workspace_id AS "workspaceId"
            FROM vw_colaboradores v
            JOIN usuario u ON u.id_usuario = v.id_usuario
            WHERE v.id_usuario = :collaboratorId AND v.workspace_id = :workspaceId
              AND v.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            """, nativeQuery = true)
    Optional<CollaboratorDetailsProjection> findDetailsInScope(@Param("collaboratorId") Long collaboratorId,
            @Param("firebaseUid") String firebaseUid, @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT v.id_usuario AS id, v.nome AS name, v.email,
                   v.cargo_id AS "roleId", v.cargo AS "roleName",
                   v.unidade_id AS "unitId", v.unidade AS "unitName", v.status, u.tipo AS "userType"
            FROM vw_colaboradores v
            JOIN usuario u ON u.id_usuario = v.id_usuario
            WHERE v.workspace_id = :workspaceId
              AND v.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            ORDER BY v.nome, v.id_usuario
            """, nativeQuery = true)
    List<CollaboratorSummaryProjection> findAllInScope(@Param("firebaseUid") String firebaseUid,
                                                     @Param("workspaceId") Long workspaceId);
}
