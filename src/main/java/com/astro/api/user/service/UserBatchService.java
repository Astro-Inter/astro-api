package com.astro.api.user.service;

import com.astro.api.user.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Service
public class UserBatchService {
    private static final int BATCH_SIZE = 500;
    private static final String INSERT_PRE_REGISTERED_USER = """
            insert into usuario (nome, email, firebase_uid, cpf, tipo, modalidade, status, criado_em, unidade_id, cargo_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserBatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAllPreRegistered(List<User> users) {
        if (users.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_PRE_REGISTERED_USER, users, BATCH_SIZE, (statement, user) -> {
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setNull(3, Types.VARCHAR);
            statement.setString(4, user.getCpf());
            statement.setString(5, user.getType().name());
            statement.setString(6, user.getWorkModel().name());
            statement.setString(7, user.getStatus().name());
            statement.setTimestamp(8, Timestamp.from(user.getCreatedAt()));
            statement.setLong(9, user.getUnit().id);
            statement.setLong(10, user.getCargo().getId());
        });
    }
}
