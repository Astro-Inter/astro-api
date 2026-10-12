package com.astro.api.home.repository;

import com.astro.api.user.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface HomeRepository extends Repository<User, Long> {
    @Query(value = "SELECT CAST(fn_carrega_workspace(:firebaseUid) AS text)", nativeQuery = true)
    String loadHome(@Param("firebaseUid") String firebaseUid);
}
