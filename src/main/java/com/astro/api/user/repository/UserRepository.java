package com.astro.api.user.repository;

import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByEmailInIgnoreCase(Collection<String> emails);

    List<User> findByCpfIn(Collection<String> cpfs);

    @Query(value = "SELECT fn_retornar_tipo_conta(:email)", nativeQuery = true)
    String findAccountTypeByEmail(@Param("email") String email);
}
