package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.role.model.Role;
import com.astro.api.role.service.RoleService;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.service.UnitSettingsService;
import com.astro.api.user.model.CollaboratorImportPlan;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.service.CollaboratorImportService;
import com.astro.api.user.service.CollaboratorSpreadsheetReader;
import com.astro.api.workspace.dto.request.WorkspaceImportRequest;
import com.astro.api.workspace.dto.response.WorkspaceImportResponse;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import com.astro.api.workspace.model.Workspace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkspaceImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceImportService.class);
    private static final long MAX_IMPORT_SIZE_BYTES = 10L * 1024 * 1024;
    private final WorkspaceAccessService workspaceAccessService;
    private final CollaboratorSpreadsheetReader collaboratorSpreadsheetReader;
    private final CollaboratorImportService collaboratorImportService;
    private final RoleService roleService;
    private final UnitSettingsService unitSettingsService;
    private final FirebaseIdentityService firebaseIdentityService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public WorkspaceImportService(WorkspaceAccessService workspaceAccessService, CollaboratorSpreadsheetReader collaboratorSpreadsheetReader,
            CollaboratorImportService collaboratorImportService, RoleService roleService,
            UnitSettingsService unitSettingsService, FirebaseIdentityService firebaseIdentityService,
            ApplicationEventPublisher applicationEventPublisher,
            PlatformTransactionManager transactionManager) {
        this.workspaceAccessService = workspaceAccessService;
        this.collaboratorSpreadsheetReader = collaboratorSpreadsheetReader;
        this.collaboratorImportService = collaboratorImportService;
        this.roleService = roleService;
        this.unitSettingsService = unitSettingsService;
        this.firebaseIdentityService = firebaseIdentityService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public WorkspaceImportResponse importFile(MultipartFile file, WorkspaceImportRequest body,
                                               AuthenticatedUser principal) {
        List<EmailChange> emailChanges = new ArrayList<>();
        try {
            WorkspaceImportResponse response = transactionTemplate.execute(status -> {
                Workspace workspace = workspaceAccessService.requireWorkspace(principal, true);
                List<CollaboratorImportRow> rows = collaboratorSpreadsheetReader.read(file, MAX_IMPORT_SIZE_BYTES);
                if (rows.isEmpty()) {
                    SpreadsheetImportMessage error = new SpreadsheetImportMessage(1, "arquivo", "A planilha não contém colaboradores");
                    throw new SpreadsheetImportException(List.of(error));
                }
                return synchronize(workspace, rows, body, emailChanges);
            });
            return response;
        } catch (RuntimeException failure) {
            compensateEmails(emailChanges, failure);
            throw failure;
        }
    }

    private WorkspaceImportResponse synchronize(Workspace workspace, List<CollaboratorImportRow> rows,
                                                WorkspaceImportRequest body, List<EmailChange> emailChanges) {
        Map<String, Unit> unitsByName = unitSettingsService.resolveForImport(workspace, body.units());
        List<String> roleNames = rows.stream().map(CollaboratorImportRow::role).toList();
        Map<String, Role> rolesByName = roleService.resolveForImport(workspace, roleNames);
        CollaboratorImportPlan plan = collaboratorImportService.plan(workspace, rows, unitsByName, rolesByName);
        List<Unit> referencedUnits = plan.changes().stream().map(CollaboratorImportPlan.Change::unit).toList();
        Map<Unit, Unit> persistedUnits = unitSettingsService.persistNewForImport(workspace, body.units(),
                unitsByName, referencedUnits);
        plan = collaboratorImportService.bindPersistedUnits(plan, persistedUnits);
        List<Role> referencedRoles = plan.changes().stream().map(CollaboratorImportPlan.Change::role).toList();
        int createdRoles = roleService.persistNewForImport(referencedRoles);
        collaboratorImportService.validateNewLinks(plan, workspace);
        synchronizeEmails(plan, emailChanges);
        collaboratorImportService.persist(plan);

        List<String> newCollaboratorEmails = plan.changes().stream()
                .filter(change -> change.existingCollaborator() == null)
                .map(change -> change.data().email()).toList();
        if (!newCollaboratorEmails.isEmpty()) {
            applicationEventPublisher.publishEvent(new PreRegisteredCollaboratorsCreatedEvent(newCollaboratorEmails));
        }
        int createdCollaborators = newCollaboratorEmails.size();
        int updatedCollaborators = plan.changes().size() - createdCollaborators;
        return new WorkspaceImportResponse(createdCollaborators, updatedCollaborators,
                plan.unchanged(), persistedUnits.size(), createdRoles, plan.ignored());
    }

    private void synchronizeEmails(CollaboratorImportPlan plan, List<EmailChange> emailChanges) {
        for (CollaboratorImportPlan.Change change : plan.changes()) {
            User existingCollaborator = change.existingCollaborator();
            if (existingCollaborator != null && existingCollaborator.getFirebaseUid() != null && !existingCollaborator.getFirebaseUid().isBlank()
                    && !change.data().email().equalsIgnoreCase(existingCollaborator.getEmail())) {
                FirebaseIdentityService.EmailState previousEmailState = firebaseIdentityService.readEmailState(existingCollaborator.getFirebaseUid());
                if (previousEmailState.email() == null || !previousEmailState.email().equalsIgnoreCase(existingCollaborator.getEmail())) {
                    throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da importação");
                }
                EmailChange emailChange = new EmailChange(existingCollaborator.getId(), existingCollaborator.getFirebaseUid(), previousEmailState);
                emailChanges.add(emailChange);
                firebaseIdentityService.updateEmail(emailChange.firebaseUid(), change.data().email());
            }
        }
    }

    private void compensateEmails(List<EmailChange> changes, RuntimeException original) {
        boolean compensationFailed = false;
        for (EmailChange change : changes.reversed()) {
            try {
                firebaseIdentityService.restoreEmail(change.firebaseUid(), change.previousEmailState());
            } catch (RuntimeException failure) {
                compensationFailed = true;
                original.addSuppressed(failure);
                try {
                    collaboratorImportService.deactivateAfterEmailFailure(change.collaboratorId(), change.firebaseUid());
                } catch (RuntimeException containment) {
                    original.addSuppressed(containment);
                }
                LOGGER.error("Falha ao compensar identidade do colaborador {} na importação; reconciliação necessária",
                        change.collaboratorId());
            }
        }
        if (compensationFailed) throw new IllegalStateException(
                "Falha de sincronização da autenticação; reconciliação necessária", original);
    }

    private record EmailChange(Long collaboratorId, String firebaseUid, FirebaseIdentityService.EmailState previousEmailState) { }
}
