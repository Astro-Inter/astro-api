package com.astro.api.event.repository;

import com.astro.api.user.model.User;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EventCandidateRepository extends Repository<User, Long> {
    @Query(value = """
            select id_usuario as id, nome as name, email,
                   cargo_id as "roleId", cargo_nome as "roleName",
                   unidade_id as "unitId", unidade_nome as "unitName", modalidade as "workModel"
            from fn_seleciona_candidatos_evento(:firebaseUid, cast(:nrId as integer))
            """, nativeQuery = true)
    List<EventCandidateProjection> findEligibleCandidates(@Param("firebaseUid") String firebaseUid,
                                                         @Param("nrId") Integer nrId);
}
