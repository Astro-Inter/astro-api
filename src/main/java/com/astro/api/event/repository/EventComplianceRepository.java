package com.astro.api.event.repository;

import com.astro.api.event.model.EventCompliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventComplianceRepository extends JpaRepository<EventCompliance, Long> {
    Optional<EventCompliance> findByConclusion_Id(Long conclusionId);
}
