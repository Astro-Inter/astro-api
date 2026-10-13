package com.astro.api.collaborator.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollaboratorRepository extends JpaRepository<User, Long> {
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
