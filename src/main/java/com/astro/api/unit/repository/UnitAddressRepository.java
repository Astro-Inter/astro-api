package com.astro.api.unit.repository;

import com.astro.api.unit.model.UnitAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitAddressRepository extends JpaRepository<UnitAddress, Long> { }
