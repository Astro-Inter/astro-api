package com.astro.api.event.repository;

import com.astro.api.event.model.Evento;
import com.astro.api.event.model.Turma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TurmaRepository extends JpaRepository<Turma, Long> {
    List<Turma> findByEventoOrderByDataInicial(Evento evento);
    Optional<Turma> findByIdAndEvento(Long id, Evento evento);
}
