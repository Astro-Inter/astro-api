package com.astro.api.role.repository;

import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.sql.SQLException;
import java.util.function.Supplier;

@Repository
public class RoleManagementRepository {
    private final RoleRepository repository;

    public RoleManagementRepository(RoleRepository repository) {
        this.repository = repository;
    }

    public List<RoleResponse> findAll(String firebaseUid) {
        return execute(() -> repository.findAllByFirebaseUid(firebaseUid).stream()
                .map(this::toResponse).toList());
    }

    public List<RoleNrResponse> findNrs(String firebaseUid, Long roleId) {
        return execute(() -> repository.findNrs(firebaseUid, roleId).stream()
                .map(nr -> new RoleNrResponse(nr.getNrCode(), nr.getDescription())).toList());
    }

    public void create(String firebaseUid, String name, String status) {
        execute(() -> {
            repository.create(firebaseUid, name, status);
            return null;
        });
    }

    public boolean toggleStatus(String firebaseUid, Long roleId) {
        return execute(() -> repository.toggleStatus(firebaseUid, roleId));
    }

    public RoleResponse update(String firebaseUid, Long roleId, String name, String status) {
        List<RoleResponse> updated = execute(() -> repository.update(firebaseUid, roleId, name, status).stream()
                .map(this::toResponse).toList());
        if (updated.isEmpty()) {
            throw new ResourceNotFoundException("Cargo não encontrado no workspace do gestor");
        }
        return updated.getFirst();
    }

    public void updateNrs(String firebaseUid, Long roleId, String nrsJson) {
        execute(() -> {
            repository.updateNrs(firebaseUid, roleId, nrsJson);
            return null;
        });
    }

    private RoleResponse toResponse(RoleProjection role) {
        return new RoleResponse(role.getRoleId(), role.getWorkspaceId(), role.getName(),
                role.getCollaboratorCount(), role.getStatus());
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
