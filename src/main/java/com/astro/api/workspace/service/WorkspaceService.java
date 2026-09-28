package com.astro.api.workspace.service;

import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.dto.response.RegisterWorkspaceResponse;
import com.astro.api.workspace.repository.WorkspaceRepository;
import com.astro.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final FirebaseIdentityService firebaseIdentityService;
    private final SpreadsheetImportService spreadsheetImportService;
    private final WorkspaceRegistrationTransactionalService transactionalService;

    public WorkspaceService(WorkspaceRepository workspaceRepository, UserRepository userRepository,
                            FirebaseIdentityService firebaseIdentityService, SpreadsheetImportService spreadsheetImportService,
                            WorkspaceRegistrationTransactionalService transactionalService) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.firebaseIdentityService = firebaseIdentityService;
        this.spreadsheetImportService = spreadsheetImportService;
        this.transactionalService = transactionalService;
    }

    public RegisterWorkspaceResponse register(CreateWorkspaceRequest request, MultipartFile file) {
        validateStructuralConflicts(request);
        List<SpreadsheetImportService.SpreadsheetRow> rows = spreadsheetImportService.read(file);
        SpreadsheetImportService.SpreadsheetRow managerRow = spreadsheetImportService.findManagerRow(rows, request.email());
        String firebaseUid = firebaseIdentityService.createUser(request.email().trim(), request.password(), managerRow.name().trim());
        try {
            return transactionalService.register(request, firebaseUid, rows, managerRow);
        } catch (RuntimeException exception) {
            firebaseIdentityService.compensateCreatedUser(firebaseUid);
            throw exception;
        }
    }

    private void validateStructuralConflicts(CreateWorkspaceRequest request) {
        if (workspaceRepository.existsByCnpj(request.workspace().cnpj())) {
            throw new ConflictException("Já existe um workspace com este CNPJ");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
    }
}
