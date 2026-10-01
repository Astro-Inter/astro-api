package com.astro.api.cargo.repository;

import com.astro.api.cargo.model.Cargo;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface CargoRepository extends JpaRepository<Cargo, Long> {
    @Query("select c from Cargo c where c.workspace = :workspace and lower(c.name) in :names")
    List<Cargo> findByWorkspaceAndNormalizedNames(@Param("workspace") Workspace workspace,
                                                   @Param("names") Collection<String> names);
}
