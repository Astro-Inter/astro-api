package com.astro.api.event.repository;

import com.astro.api.event.model.Evento;
import com.astro.api.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {
    Optional<Evento> findByIdAndWorkspace(Long id, Workspace workspace);
    List<Evento> findByWorkspace(Workspace workspace);
}
