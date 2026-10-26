package com.astro.api.conformity.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConformityUserRepository extends Repository<User, Long> {
    @Query(value = """
            SELECT u.* FROM usuario u
            JOIN unidade un ON un.id_unidade = u.unidade_id
            WHERE u.id_usuario = :collaboratorId
              AND u.tipo IN ('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')
              AND un.workspace_id = :workspaceId
              AND u.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            FOR UPDATE OF u
            """, nativeQuery = true)
    Optional<User> findForUpdateInScope(@Param("collaboratorId") Long collaboratorId,
            @Param("firebaseUid") String firebaseUid, @Param("workspaceId") Long workspaceId);

    @Query(value = """
            SELECT u.id_usuario FROM usuario u
            JOIN unidade un ON un.id_unidade = u.unidade_id
            WHERE u.cpf = :cpf
              AND u.tipo IN ('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')
              AND un.workspace_id = :workspaceId
              AND u.unidade_id IN (SELECT id_unidade FROM fn_seleciona_unidades(:firebaseUid))
            """, nativeQuery = true)
    Optional<Long> findIdByCpfInScope(@Param("cpf") String cpf, @Param("firebaseUid") String firebaseUid,
                                    @Param("workspaceId") Long workspaceId);
}
