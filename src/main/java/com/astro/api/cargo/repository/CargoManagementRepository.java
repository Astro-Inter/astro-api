package com.astro.api.cargo.repository;

import com.astro.api.cargo.dto.response.CargoNrResponse;
import com.astro.api.cargo.dto.response.CargoResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.sql.SQLException;
import java.util.function.Supplier;

@Repository
public class CargoManagementRepository {
    private static final RowMapper<CargoResponse> CARGO_MAPPER = (rs, row) -> new CargoResponse(
            rs.getLong("id_cargo"), rs.getLong("id_workspace"), rs.getString("cargo"),
            rs.getLong("quantidade_colaboradores"), rs.getString("status"));
    private final JdbcTemplate jdbc;

    public CargoManagementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CargoResponse> findAll(String firebaseUid) {
        return execute(() -> jdbc.query("SELECT * FROM fn_seleciona_cargos(?)", CARGO_MAPPER, firebaseUid));
    }

    public List<CargoNrResponse> findNrs(String firebaseUid, Long cargoId) {
        return execute(() -> jdbc.query("SELECT * FROM fn_seleciona_cargo_nrs(?, ?)",
                (rs, row) -> new CargoNrResponse(rs.getInt("codigo_nr"), rs.getString("descricao")),
                firebaseUid, cargoId));
    }

    public void create(String firebaseUid, String name, String status) {
        execute(() -> jdbc.update("CALL pr_inseri_cargo(?, ?, ?)", firebaseUid, name, status));
    }

    public boolean toggleStatus(String firebaseUid, Long cargoId) {
        return execute(() -> jdbc.queryForObject("SELECT fn_altera_status_cargo(?, ?)",
                Boolean.class, firebaseUid, cargoId));
    }

    public CargoResponse update(String firebaseUid, Long cargoId, String name, String status) {
        List<CargoResponse> updated = execute(() -> jdbc.query("SELECT * FROM fn_altera_cargo(?, ?, ?, ?)",
                CARGO_MAPPER, firebaseUid, cargoId, name, status));
        if (updated.isEmpty()) {
            throw new ResourceNotFoundException("Cargo não encontrado no workspace do gestor");
        }
        return updated.getFirst();
    }

    public void updateNrs(String firebaseUid, Long cargoId, String nrsJson) {
        execute(() -> jdbc.update("CALL pr_alterar_cargo_nrs(?, ?, CAST(? AS JSONB))", firebaseUid, cargoId, nrsJson));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException ex) {
            // Translate only the explicit SQLSTATEs raised by the cargo routines.
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    String state = sql.getSQLState();
                    if ("22023".equals(state)) {
                        throw new BusinessException("Dados inválidos para a operação de cargo");
                    }
                    if ("42501".equals(state)) {
                        throw new AccessDeniedException("Gestor ativo sem acesso à operação");
                    }
                    if ("P0002".equals(state)) {
                        throw new ResourceNotFoundException("Cargo não encontrado no workspace do gestor");
                    }
                }
            }
            throw ex;
        }
    }
}
