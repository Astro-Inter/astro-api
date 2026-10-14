package com.astro.api.event.repository;

import com.astro.api.user.model.User;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EventCandidateRepository extends Repository<User, Long> {
    // cargo_nr has no entity mapping; EXISTS preserves one row per candidate.
    @Query(value = """
            select u.id_usuario as id, u.nome as name, u.email as email,
                   r.id_cargo as "roleId", r.nome as "roleName",
                   un.id_unidade as "unitId", un.nome as "unitName", u.modalidade as "workModel"
            from usuario u
            join unidade un on un.id_unidade = u.unidade_id
            left join cargo r on r.id_cargo = u.cargo_id
            where un.workspace_id = :workspaceId and u.status = 'ATIVO' and u.id_usuario <> :managerId
              and (cast(:nrId as integer) is null or exists (
                   select 1 from cargo_nr rn where rn.cargo_id = u.cargo_id and rn.nr_id = :nrId))
            order by u.nome, u.id_usuario
            """, nativeQuery = true)
    List<EventCandidateProjection> findEligibleCandidates(@Param("workspaceId") Long workspaceId,
            @Param("managerId") Long managerId, @Param("nrId") Integer nrId);
}
