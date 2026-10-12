package com.astro.api.unit.repository;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Supplier;

@Repository
public class UnitManagementRepository {
    private final UnitRepository repository;

    public UnitManagementRepository(UnitRepository repository) {
        this.repository = repository;
    }

    public List<UnitResponse> findAll(String firebaseUid) {
        return execute(() -> repository.findAllByFirebaseUid(firebaseUid).stream()
                .map(unit -> new UnitResponse(unit.getUnitId(), unit.getWorkspaceId(), unit.getName(),
                        unit.getStatus(), unit.getCollaboratorCount(), unit.getPostalCode(), unit.getStreet(),
                        unit.getCity(), unit.getNeighborhood(), unit.getState(), unit.getAddressLine2()))
                .toList());
    }

    public boolean toggleStatus(String firebaseUid, Long unitId) {
        Boolean status = execute(() -> repository.toggleStatus(firebaseUid, unitId));
        if (status == null) {
            throw new IllegalStateException("A função de status da unidade não retornou um booleano");
        }
        return status;
    }

    public List<UnitNrResponse> findNrs(String firebaseUid, Long unitId) {
        return execute(() -> repository.findNrs(firebaseUid, unitId).stream()
                .map(nr -> new UnitNrResponse(nr.getNrCode(), nr.getDescription())).toList());
    }

    public void updateNrs(String firebaseUid, Long unitId, String nrsJson) {
        execute(() -> {
            repository.updateNrs(firebaseUid, unitId, nrsJson);
            return null;
        });
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    if ("22023".equals(sql.getSQLState())) {
                        throw new BusinessException("Dados inválidos para a operação de unidade ou NR inválida");
                    }
                    if ("42501".equals(sql.getSQLState())) {
                        throw new AccessDeniedException("Gestor ativo sem acesso à operação");
                    }
                    if ("P0002".equals(sql.getSQLState())) {
                        throw new ResourceNotFoundException("Unidade não encontrada no workspace do gestor");
                    }
                }
            }
            throw exception;
        }
    }
}
