package com.astro.api.collaborator.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollaboratorRepository extends JpaRepository<User, Long> {
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
                   v.modalidade AS "workModel", v.status,
                   v.cargo_id AS "roleId", v.cargo AS "roleName",
                   v.unidade_id AS "unitId", v.unidade AS "unitName", v.workspace_id AS "workspaceId"
            FROM vw_colaboradores v
            WHERE v.id_usuario = :collaboratorId AND v.workspace_id = :workspaceId
              AND v.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            """, nativeQuery = true)
    Optional<CollaboratorDetailsProjection> findDetailsInScope(@Param("collaboratorId") Long collaboratorId,
            @Param("firebaseUid") String firebaseUid, @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT v.id_usuario AS id, v.nome AS name, v.email,
                   v.cargo_id AS "roleId", v.cargo AS "roleName",
                   v.unidade_id AS "unitId", v.unidade AS "unitName", v.status
            FROM vw_colaboradores v
            WHERE v.workspace_id = :workspaceId
              AND v.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            ORDER BY v.nome, v.id_usuario
            """, nativeQuery = true)
    List<CollaboratorSummaryProjection> findAllInScope(@Param("firebaseUid") String firebaseUid,
                                                     @Param("workspaceId") Long workspaceId);
}
