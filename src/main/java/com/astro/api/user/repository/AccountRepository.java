package com.astro.api.user.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<User, Long> {
    // Native queries below retain PostgreSQL row locks required by concurrent updates.
    @Query(value = "SELECT u.* FROM usuario u WHERE u.firebase_uid = :firebaseUid FOR UPDATE OF u", nativeQuery = true)
    Optional<User> findForUpdateByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    @Query(value = "SELECT ativo FROM cargo WHERE id_cargo = :roleId AND workspace_id = :workspaceId FOR SHARE", nativeQuery = true)
    Boolean lockRoleActive(@Param("roleId") Long roleId, @Param("workspaceId") Long workspaceId);

    @Query(value = "SELECT ativo FROM unidade WHERE id_unidade = :unitId AND workspace_id = :workspaceId FOR SHARE", nativeQuery = true)
    Boolean lockUnitActive(@Param("unitId") Long unitId, @Param("workspaceId") Long workspaceId);

    // Simple existence checks use derived JPA queries instead of handwritten SQL.
    boolean existsByCpfAndIdNot(String cpf, Long userId);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long userId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE usuario SET status = 'DESATIVADO'
            WHERE id_usuario = :userId AND firebase_uid = :firebaseUid AND tipo IN ('GESTOR', 'GESTOR_WORKSPACE')
            """, nativeQuery = true)
    int deactivateAfterEmailFailure(@Param("userId") Long userId, @Param("firebaseUid") String firebaseUid);
}
