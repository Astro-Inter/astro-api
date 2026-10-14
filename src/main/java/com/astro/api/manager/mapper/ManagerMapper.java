package com.astro.api.manager.mapper;

import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.manager.repository.ManagerDetailsProjection;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.model.User;
import com.astro.api.manager.dto.request.UpdateManagerRequest;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.manager.repository.ManagerSummaryProjection;
import com.astro.api.user.model.UserStatus;
import org.springframework.stereotype.Component;
import static com.astro.api.user.mapper.UserAvatarMapper.initials;

@Component
public class ManagerMapper {
    public ManagerSummaryResponse toSummary(ManagerSummaryProjection row, String profilePhotoUrl) {
        return new ManagerSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getUnitId(), row.getUnitName(), UserStatus.valueOf(row.getStatus()), profilePhotoUrl, initials(row.getName()));
    }

    public ManagerDetailsResponse toDetails(ManagerDetailsProjection row, String profilePhotoUrl) {
        return new ManagerDetailsResponse(row.getId(), row.getName(), row.getCpf(), row.getEmail(),
                row.getWorkModel() == null ? null : WorkModel.valueOf(row.getWorkModel()),
                UserStatus.valueOf(row.getStatus()), row.getRoleId(), row.getRoleName(),
                row.getUnitId(), row.getUnitName(), row.getWorkspaceId(), profilePhotoUrl, initials(row.getName()));
    }

    public ManagerDetailsResponse toDetails(User user, String profilePhotoUrl) {
        Role role = user.getRole();
        Unit unit = user.getUnit();
        return new ManagerDetailsResponse(user.getId(), user.getName(), user.getCpf(), user.getEmail(),
                user.getWorkModel(), user.getStatus(), role == null ? null : role.getId(),
                role == null ? null : role.getName(), unit == null ? null : unit.id,
                unit == null ? null : unit.name, unit == null || unit.workspace == null ? null : unit.workspace.id,
                profilePhotoUrl, initials(user.getName()));
    }

    public void applyUpdate(User manager, UpdateManagerRequest body, Role role, Unit unit) {
        manager.setName(body.name()); manager.setCpf(body.cpf()); manager.setEmail(body.email());
        manager.setRole(role); manager.setUnit(unit); manager.setWorkModel(body.workModel());
    }
}
