package com.astro.api.workspace.mapper;

import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.CollaboratorImportData;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceManagerMapper {
    private final CollaboratorMapper collaboratorMapper;

    public WorkspaceManagerMapper(CollaboratorMapper collaboratorMapper) {
        this.collaboratorMapper = collaboratorMapper;
    }

    public User toNewManager(CollaboratorImportRow row, String firebaseUid, Role role, Unit unit) {
        CollaboratorImportData data = collaboratorMapper.toRegistrationData(row);
        User manager = collaboratorMapper.toNewUser(data, role, unit);

        if (data.cpf().isBlank()) {
            manager.setCpf(null);
        }

        manager.setFirebaseUid(firebaseUid);
        manager.setType(UserType.GESTOR_WORKSPACE);
        manager.setStatus(UserStatus.ATIVO);
        return manager;
    }
}
