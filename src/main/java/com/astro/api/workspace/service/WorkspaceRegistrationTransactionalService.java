package com.astro.api.workspace.service;

import com.astro.api.role.model.Role;
import com.astro.api.role.service.RoleService;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.service.UnitService;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserBatchService;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.dto.response.RegisterWorkspaceResponse;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.mapper.WorkspaceManagerMapper;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceRegistrationTransactionalService {
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final UnitService unitService;
    private final SpreadsheetImportService spreadsheetImportService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceMapper workspaceMapper;
    private final RoleService roleService;
    private final UserBatchService userBatchService;
    private final WorkspaceManagerMapper workspaceManagerMapper;

    public WorkspaceRegistrationTransactionalService(WorkspaceRepository workspaceRepository, UserRepository userRepository,
                                                     UnitService unitService, SpreadsheetImportService spreadsheetImportService,
                                                     ApplicationEventPublisher eventPublisher, WorkspaceMapper workspaceMapper,
                                                     RoleService roleService, UserBatchService userBatchService,
                                                     WorkspaceManagerMapper workspaceManagerMapper) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.unitService = unitService;
        this.spreadsheetImportService = spreadsheetImportService;
        this.eventPublisher = eventPublisher;
        this.workspaceMapper = workspaceMapper;
        this.roleService = roleService;
        this.userBatchService = userBatchService;
        this.workspaceManagerMapper = workspaceManagerMapper;
    }

    @Transactional
    public RegisterWorkspaceResponse register(CreateWorkspaceRequest request, String firebaseUid,
                                              List<CollaboratorImportRow> rows,
                                              CollaboratorImportRow managerRow,
                                              SpreadsheetImportService.ImportProcessingResult importResult) {
        Workspace workspace = workspaceRepository.save(workspaceMapper.toEntity(request.workspace()));

        List<Unit> units = unitService.createForWorkspace(workspace, request.units());
        Unit managerUnit = spreadsheetImportService.findManagerUnit(managerRow, units);
        Map<String, Role> rolesByName = roleService.createForWorkspace(workspace,
                rows.stream().map(CollaboratorImportRow::role).toList());
        Role managerRole = spreadsheetImportService.findManagerRole(managerRow, rolesByName);
        User manager = workspaceManagerMapper.toNewManager(managerRow, firebaseUid, managerRole, managerUnit);
        userRepository.save(manager);

        spreadsheetImportService.bindPersistedDependencies(importResult.validUsers(), units, rolesByName);
        userBatchService.saveAllPreRegistered(importResult.validUsers());
        eventPublisher.publishEvent(new PreRegisteredCollaboratorsCreatedEvent(
                importResult.validUsers().stream().map(User::getEmail).toList()));
        return new RegisterWorkspaceResponse(workspace.id, List.of());
    }
}
