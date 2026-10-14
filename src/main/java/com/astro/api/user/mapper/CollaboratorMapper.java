package com.astro.api.user.mapper;

import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.repository.CollaboratorSummaryProjection;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.repository.CollaboratorDetailsProjection;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserType;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import java.time.Instant;
import static com.astro.api.user.mapper.UserAvatarMapper.initials;
import org.springframework.stereotype.Component;

@Component
public class CollaboratorMapper {
    public void applyUpdate(User user, UpdateCollaboratorRequest body, Role role, Unit unit) {
        user.setName(body.name());
        user.setCpf(body.cpf());
        user.setEmail(body.email());
        user.setRole(role);
        user.setUnit(unit);
        user.setWorkModel(body.workModel());
    }

    public User toNewUser(CreateCollaboratorRequest body, Role role, Unit unit) {
        User user = new User();
        user.setName(body.name());
        user.setCpf(body.cpf());
        user.setEmail(body.email());
        user.setWorkModel(body.workModel());
        user.setRole(role);
        user.setUnit(unit);
        user.setType(UserType.COLABORADOR);
        user.setStatus(UserStatus.PRE_CADASTRADO);
        user.setCreatedAt(Instant.now());
        return user;
    }

    public CollaboratorDetailsResponse toDetails(User user, String profilePhotoUrl) {
        return new CollaboratorDetailsResponse(user.getId(), user.getName(), user.getCpf(), user.getEmail(),
                user.getWorkModel(), user.getStatus(), user.getRole().getId(), user.getRole().getName(),
                user.getUnit().id, user.getUnit().name, user.getUnit().workspace.id, profilePhotoUrl, initials(user.getName()));
    }

    public CollaboratorDetailsResponse toDetails(CollaboratorDetailsProjection row, String profilePhotoUrl) {
        return new CollaboratorDetailsResponse(row.getId(), row.getName(), row.getCpf(), row.getEmail(),
                row.getWorkModel() == null ? null : WorkModel.valueOf(row.getWorkModel()),
                UserStatus.valueOf(row.getStatus()), row.getRoleId(), row.getRoleName(),
                row.getUnitId(), row.getUnitName(), row.getWorkspaceId(), profilePhotoUrl, initials(row.getName()));
    }

    public CollaboratorSummaryResponse toSummary(CollaboratorSummaryProjection row, String profilePhotoUrl) {
        return new CollaboratorSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getRoleId(), row.getRoleName(), row.getUnitId(), row.getUnitName(),
                UserStatus.valueOf(row.getStatus()), profilePhotoUrl, initials(row.getName()));
    }

}
