package com.astro.api.auth.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthRepository extends JpaRepository<User, Long> {
    @Query(value = "SELECT fn_retornar_nivel_acesso(:firebaseUid)", nativeQuery = true)
    String findAccessLevel(@Param("firebaseUid") String firebaseUid);
}
