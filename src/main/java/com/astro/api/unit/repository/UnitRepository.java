package com.astro.api.unit.repository;

import com.astro.api.unit.model.Unit;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByWorkspaceAndNameIn(Workspace workspace, Collection<String> names);
}
