package com.astro.api.user.mapper;

import com.astro.api.role.model.Role;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.EventCompletionStatus;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.ParticipationStatus;
import com.astro.api.common.text.NormalizedText;
import com.astro.api.common.text.PersonalDataNormalizer;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.dto.response.CollaboratorEventResponse;
import com.astro.api.user.dto.response.CollaboratorEventsResponse;
import com.astro.api.user.dto.response.CollaboratorPendingCountResponse;
import com.astro.api.user.dto.response.CollaboratorProfileResponse;
import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.model.CollaboratorImportData;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.CollaboratorDetailsProjection;
import com.astro.api.user.repository.CollaboratorEventProjection;
import com.astro.api.user.repository.CollaboratorPendingCountProjection;
import com.astro.api.user.repository.CollaboratorSummaryProjection;
import com.astro.api.user.dto.response.UserProfileResponse;
import java.util.List;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Component;

import static com.astro.api.user.mapper.UserAvatarMapper.initials;

@Component
public class CollaboratorMapper {
    public CollaboratorEventResponse toEvent(CollaboratorEventProjection row) {
        return new CollaboratorEventResponse(
                row.getEventId(),
                row.getEventTitle(),
                row.getClassLimitDate(),
                toParticipationStatus(row.getCompletionStatus()),
                EventStatus.valueOf(row.getEventStatus()),
                CompletionMode.valueOf(row.getCompletionMode()));
    }

    public CollaboratorEventsResponse toEvents(CollaboratorDetailsProjection collaborator,
                                                List<CollaboratorEventProjection> rows) {
        List<CollaboratorEventResponse> events = rows.stream().map(this::toEvent).toList();
        return new CollaboratorEventsResponse(collaborator.getId(), collaborator.getName(), events);
    }

    private ParticipationStatus toParticipationStatus(String completionStatus) {
        if (completionStatus == null) {
            return ParticipationStatus.PENDENTE;
        }
        return switch (EventCompletionStatus.valueOf(completionStatus)) {
            case PENDENTE -> ParticipationStatus.EM_ANALISE;
            case CONCLUIDO -> ParticipationStatus.CONCLUIDO;
            case REJEITADO -> ParticipationStatus.REJEITADO;
        };
    }

    public CollaboratorImportData toImportData(CollaboratorImportRow row) {
        return new CollaboratorImportData(row.name(), row.cpf(), row.email(), workModel(row.workModel()));
    }

    public CollaboratorImportData toRegistrationData(CollaboratorImportRow row) {
        String cpf = PersonalDataNormalizer.cpfDigits(row.cpf());
        return new CollaboratorImportData(row.name(), cpf, row.email(), workModel(row.workModel()));
    }

    private WorkModel workModel(String value) {
        try {
            return WorkModel.valueOf(NormalizedText.key(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void applyUpdate(User user, UpdateCollaboratorRequest body, Role role, Unit unit) {
        user.setCpf(body.cpf());
        applyDetails(user, body.name(), body.email(), body.workModel(), role, unit);
    }

    public void applyImportUpdate(User user, CollaboratorImportData body, Role role, Unit unit) {
        applyDetails(user, body.name(), body.email(), body.workModel(), role, unit);
    }

    public User toNewUser(CreateCollaboratorRequest body, Role role, Unit unit) {
        CollaboratorImportData data = new CollaboratorImportData(body.name(), body.cpf(), body.email(), body.workModel());
        return toNewUser(data, role, unit);
    }

    public User toNewUser(CollaboratorImportData body, Role role, Unit unit) {
        User user = new User();
        applyImportUpdate(user, body, role, unit);
        user.setCpf(body.cpf());
        user.setType(UserType.COLABORADOR);
        user.setStatus(UserStatus.PRE_CADASTRADO);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private void applyDetails(User user, String name, String email, WorkModel workModel, Role role, Unit unit) {
        user.setName(name);
        user.setEmail(email);
        user.setWorkModel(workModel);
        user.setRole(role);
        user.setUnit(unit);
    }

    public CollaboratorDetailsResponse toDetails(User user, String profilePhotoUrl, boolean canManage) {
        return new CollaboratorDetailsResponse(user.getId(), user.getName(), user.getCpf(), user.getEmail(),
                user.getWorkModel(), user.getStatus(), user.getRole().getId(), user.getRole().getName(),
                user.getUnit().id, user.getUnit().name, user.getUnit().workspace.id, profilePhotoUrl, initials(user.getName()), canManage);
    }

    public CollaboratorDetailsResponse toDetails(CollaboratorDetailsProjection row, String profilePhotoUrl, boolean canManage) {
        return new CollaboratorDetailsResponse(row.getId(), row.getName(), row.getCpf(), row.getEmail(),
                row.getWorkModel() == null ? null : WorkModel.valueOf(row.getWorkModel()),
                UserStatus.valueOf(row.getStatus()), row.getRoleId(), row.getRoleName(),
                row.getUnitId(), row.getUnitName(), row.getWorkspaceId(), profilePhotoUrl, initials(row.getName()), canManage);
    }

    public CollaboratorSummaryResponse toSummary(CollaboratorSummaryProjection row, String profilePhotoUrl, boolean canManage) {
        return new CollaboratorSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getRoleId(), row.getRoleName(), row.getUnitId(), row.getUnitName(),
                UserStatus.valueOf(row.getStatus()), profilePhotoUrl, initials(row.getName()), canManage);
    }

    public CollaboratorPendingCountResponse toPendingCount(CollaboratorPendingCountProjection row, String profilePhotoUrl) {
        return new CollaboratorPendingCountResponse(row.getId(), row.getName(), profilePhotoUrl,
                row.getRoleName(), row.getPendingCount());
    }

    public CollaboratorProfileResponse toProfile(CollaboratorDetailsProjection row, String profilePhotoUrl,
                                                 Long pendingCount,
                                                 List<UserProfileResponse.NrProfileResponse> nrs) {
        WorkModel workModel = row.getWorkModel() == null ? null : WorkModel.valueOf(row.getWorkModel());
        return new CollaboratorProfileResponse(row.getId(), row.getName(), row.getRoleName(), profilePhotoUrl,
                row.getUnitName(), workModel, row.getEmail(), pendingCount, nrs);
    }

}
