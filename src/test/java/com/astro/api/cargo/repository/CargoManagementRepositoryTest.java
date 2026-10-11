package com.astro.api.cargo.repository;

import com.astro.api.cargo.dto.response.CargoResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CargoManagementRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CargoManagementRepository repository = new CargoManagementRepository(jdbc);

    @Test
    @SuppressWarnings("unchecked")
    void mapsTheDatabaseViewCargoColumnToNome() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id_cargo")).thenReturn(12L);
        when(rs.getLong("id_workspace")).thenReturn(3L);
        when(rs.getString("cargo")).thenReturn("Gerente");
        when(rs.getLong("quantidade_colaboradores")).thenReturn(25L);
        when(rs.getString("status")).thenReturn("Ativo");
        when(jdbc.query(eq("SELECT * FROM fn_seleciona_cargos(?)"), any(RowMapper.class), eq("gestor")))
                .thenAnswer(call -> List.of(((RowMapper<CargoResponse>) call.getArgument(1)).mapRow(rs, 0)));
        assertEquals(new CargoResponse(12L, 3L, "Gerente", 25, "Ativo"), repository.findAll("gestor").getFirst());
    }

    @Test
    void translatesTheSqlstatesRaisedByDatabaseRoutines() {
        for (String state : List.of("22023", "42501", "P0002")) {
            reset(jdbc);
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("gestor"), eq(12L)))
                    .thenThrow(new UncategorizedSQLException("toggle", "SQL", new SQLException("banco", state)));
            Class<? extends RuntimeException> expected = switch (state) {
                case "22023" -> BusinessException.class;
                case "42501" -> AccessDeniedException.class;
                default -> ResourceNotFoundException.class;
            };
            assertThrows(expected, () -> repository.toggleStatus("gestor", 12L));
        }
    }

    @Test
    void preservesConnectionFailuresForTheExisting503Handler() {
        var failure = new DataAccessResourceFailureException("offline");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("gestor"), eq(12L))).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> repository.toggleStatus("gestor", 12L)));
    }

    @Test
    void bindsTheNrListAsJsonbInsteadOfInterpolatingJsonIntoSql() {
        String json = "[{\"nr\":17,\"ativo\":false}]";
        repository.updateNrs("gestor", 12L, json);
        verify(jdbc).update("CALL pr_alterar_cargo_nrs(?, ?, CAST(? AS JSONB))", "gestor", 12L, json);
    }
}
