package com.astro.api.manager.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ManagerRepository extends JpaRepository<User, Long> {
    @Query(value = """
            SELECT id_usuario AS id, nome AS name, email,
                   unidade_id AS unitId, unidade AS unitName, status
            FROM vw_gestores
            WHERE workspace_id = :workspaceId
            ORDER BY nome, id_usuario
            """, nativeQuery = true)
    List<ManagerSummaryProjection> findAllInWorkspace(@Param("workspaceId") Long workspaceId);
}
