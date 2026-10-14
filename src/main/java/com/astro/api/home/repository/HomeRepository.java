package com.astro.api.home.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HomeRepository extends Repository<User, Long> {
    @Query(value = "SELECT CAST(carrega_home_colaborador(:firebaseUid) AS TEXT)", nativeQuery = true)
    String loadCollaboratorHome(@Param("firebaseUid") String firebaseUid);

    @Query(value = "SELECT id_usuario AS \"userId\" FROM usuario WHERE firebase_uid = :firebaseUid", nativeQuery = true)
    Optional<UserIdProjection> findUserIdByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    interface UserIdProjection {
        Long getUserId();
    }

    @Query(value = "SELECT CAST(fn_carrega_workspace(:firebaseUid) AS text)", nativeQuery = true)
    String loadHome(@Param("firebaseUid") String firebaseUid);

    @Query(value = "SELECT CAST(carrega_home_gestor(:firebaseUid) AS text)", nativeQuery = true)
    String loadManagerHome(@Param("firebaseUid") String firebaseUid);

    Optional<HomeUserIdProjection> findByFirebaseUid(String firebaseUid);
}
