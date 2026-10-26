package com.astro.api.unit.repository;

import com.astro.api.unit.model.UnitAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitAddressRepository extends JpaRepository<UnitAddress, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE unidade_endereco
            SET geolocalizacao = point(:longitude, :latitude)
            WHERE unidade_id = :unitId
            """, nativeQuery = true)
    int updateGeolocation(@Param("unitId") Long unitId, @Param("longitude") double longitude,
                          @Param("latitude") double latitude);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE unidade_endereco SET geolocalizacao = NULL WHERE unidade_id = :unitId", nativeQuery = true)
    int clearGeolocation(@Param("unitId") Long unitId);
}
