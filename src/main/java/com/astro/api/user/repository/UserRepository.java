package com.astro.api.user.repository;

import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByEmail(String email);

    @Query(value = "SELECT fn_retornar_tipo_conta(:email)", nativeQuery = true)
    String findAccountTypeByEmail(@Param("email") String email);
}
