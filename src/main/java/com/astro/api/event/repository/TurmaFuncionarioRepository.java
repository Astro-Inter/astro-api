package com.astro.api.event.repository;

import com.astro.api.event.model.Turma;
import com.astro.api.event.model.TurmaFuncionario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurmaFuncionarioRepository extends JpaRepository<TurmaFuncionario, Long> {
    List<TurmaFuncionario> findByTurmaIn(List<Turma> turmas);
    List<TurmaFuncionario> findByTurma(Turma turma);
    long countByTurma(Turma turma);
    boolean existsByTurmaAndUsuario_Id(Turma turma, Long usuarioId);
    void deleteByTurmaAndUsuario_Id(Turma turma, Long usuarioId);
}
