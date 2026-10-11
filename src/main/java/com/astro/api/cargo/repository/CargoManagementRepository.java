package com.astro.api.cargo.repository;

import com.astro.api.cargo.dto.response.CargoNrResponse;
import com.astro.api.cargo.dto.response.CargoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CargoManagementRepository {
    private final JdbcTemplate jdbc;

    public CargoManagementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CargoResponse> findAll(String firebaseUid) {
        return jdbc.query("SELECT * FROM fn_seleciona_cargos(?)", (rs, row) -> new CargoResponse(
                rs.getLong("id_cargo"), rs.getLong("id_workspace"), rs.getString("cargo"),
                rs.getLong("quantidade_colaboradores"), rs.getString("status")), firebaseUid);
    }

    public List<CargoNrResponse> findNrs(String firebaseUid, Long cargoId) {
        return jdbc.query("SELECT * FROM fn_seleciona_cargo_nrs(?, ?)",
                (rs, row) -> new CargoNrResponse(rs.getInt("codigo_nr"), rs.getString("descricao")),
                firebaseUid, cargoId);
    }
}
