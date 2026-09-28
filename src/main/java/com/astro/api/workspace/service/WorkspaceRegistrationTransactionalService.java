package com.astro.api.workspace.service;

import com.astro.api.cargo.model.Cargo;
import com.astro.api.cargo.service.CargoService;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.service.UnitService;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.dto.response.RegisterWorkspaceResponse;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.repository.WorkspaceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WorkspaceRegistrationTransactionalService {
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final UnitService unitService;
    private final SpreadsheetImportService spreadsheetImportService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceMapper workspaceMapper;
    private final CargoService cargoService;

    public WorkspaceRegistrationTransactionalService(WorkspaceRepository workspaceRepository, UserRepository userRepository,
                                                     UnitService unitService, SpreadsheetImportService spreadsheetImportService,
                                                     ApplicationEventPublisher eventPublisher, WorkspaceMapper workspaceMapper,
                                                     CargoService cargoService) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.unitService = unitService;
        this.spreadsheetImportService = spreadsheetImportService;
        this.eventPublisher = eventPublisher;
        this.workspaceMapper = workspaceMapper;
        this.cargoService = cargoService;
    }

    @Transactional
    public RegisterWorkspaceResponse register(CreateWorkspaceRequest request, String firebaseUid,
                                              List<SpreadsheetImportService.SpreadsheetRow> rows,
                                              SpreadsheetImportService.SpreadsheetRow managerRow) {
        Workspace workspace = workspaceRepository.save(workspaceMapper.toEntity(request.workspace()));

        List<Unit> units = unitService.createForWorkspace(workspace, request.units());
        Unit managerUnit = spreadsheetImportService.findManagerUnit(managerRow, units);
        Map<String, Cargo> cargosByName = cargoService.createForWorkspace(workspace,
                rows.stream().map(SpreadsheetImportService.SpreadsheetRow::cargo).toList());
        Cargo managerCargo = spreadsheetImportService.findManagerCargo(managerRow, cargosByName);
        User manager = new User();
        manager.setName(managerRow.name().trim());
        manager.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        manager.setFirebaseUid(firebaseUid);
        manager.setType(UserType.GESTOR_WORKSPACE);
        manager.setStatus(UserStatus.ATIVO);
        manager.setUnit(managerUnit);
        manager.setCargo(managerCargo);
        manager.setCreatedAt(Instant.now());
        userRepository.save(manager);

        SpreadsheetImportService.ImportProcessingResult result = spreadsheetImportService.validateAndBuild(
                rows, workspace, units, managerRow, cargosByName);
        List<User> savedUsers = result.validUsers().isEmpty() ? List.of() : userRepository.saveAll(result.validUsers());
        eventPublisher.publishEvent(new PreRegisteredCollaboratorsCreatedEvent(savedUsers.stream().map(User::getEmail).toList()));
        return new RegisterWorkspaceResponse(workspace.id, result.errors());
    }
}
