package com.astro.api.role.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.text.NormalizedText;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.model.NrDocument;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.conformity.repository.NrDocumentRepository;
import com.astro.api.role.mapper.RoleNrMapper;
import com.astro.api.role.dto.request.RoleNrRequest;
import com.astro.api.role.dto.request.RoleRequest;
import com.astro.api.role.dto.request.UpdateRoleNrsRequest;
import com.astro.api.role.dto.response.RoleNrOptionResponse;
import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleManagementRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.workspace.model.Workspace;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final RoleManagementRepository managementRepository;
    private final NrDocumentRepository nrDocuments;
    private final NrCatalogRepository nrCatalog;
    private final RoleNrMapper nrMapper;
    private final JsonMapper objectMapper;

    public RoleService(RoleRepository roleRepository, RoleManagementRepository managementRepository,
                        NrDocumentRepository nrDocuments, NrCatalogRepository nrCatalog,
                        RoleNrMapper nrMapper, JsonMapper objectMapper) {
        this.roleRepository = roleRepository;
        this.managementRepository = managementRepository;
        this.nrDocuments = nrDocuments;
        this.nrCatalog = nrCatalog;
        this.nrMapper = nrMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void create(RoleRequest body, AuthenticatedUser user) {
        checkIdentity(body.firebaseUid(), user);
        checkName(body.name());
        managementRepository.create(body.firebaseUid(), body.name().trim(), body.status().trim());
    }

    @Transactional
    public boolean toggleStatus(String uid, Long roleId, AuthenticatedUser user) {
        checkIdentity(uid, user);
        checkRoleId(roleId);
        return managementRepository.toggleStatus(uid, roleId);
    }

    @Transactional
    public RoleResponse update(Long roleId, RoleRequest body, AuthenticatedUser user) {
        checkIdentity(body.firebaseUid(), user);
        checkRoleId(roleId);
        checkName(body.name());
        return managementRepository.update(body.firebaseUid(), roleId, body.name().trim(), body.status().trim());
    }

    @Transactional
    public void updateNrs(Long roleId, UpdateRoleNrsRequest body, AuthenticatedUser user) {
        checkIdentity(body.firebaseUid(), user);
        checkRoleId(roleId);
        Set<Integer> codes = new HashSet<>();
        for (RoleNrRequest nr : body.nrs()) {
            if (!codes.add(nr.nr())) {
                throw new BusinessException("A lista não pode repetir códigos de NR");
            }
        }
        Set<Integer> availableCodes = findPersistedNrCodes(codes);
        List<RoleNrRequest> requestedNrs = body.nrs();

        List<Integer> unavailableCodes = requestedNrs.stream()
                .filter(RoleNrRequest::active)
                .map(RoleNrRequest::nr)
                .filter(code -> !availableCodes.contains(code))
                .sorted()
                .toList();

        if (!unavailableCodes.isEmpty()) {
            String unavailableCodesText = unavailableCodes.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));

            String errorMessage = "NRs indisponíveis no catálogo: " + unavailableCodesText
                    + ". Atualize a lista e tente novamente.";

            throw new BusinessException(errorMessage);
        }

        List<RoleNrRequest> changes = requestedNrs.stream()
                .filter(nr -> availableCodes.contains(nr.nr()))
                .toList();

        try {
            String changesJson = objectMapper.writeValueAsString(changes.stream()
                    .map(nr -> {
                        Map<String, Object> serializedNr = new LinkedHashMap<>();
                        serializedNr.put("nr", nr.nr());
                        serializedNr.put("ativo", nr.active());
                        return serializedNr;
                    }).toList());

            managementRepository.updateNrs(
                    body.firebaseUid(),
                    roleId,
                    changesJson
            );
        } catch (JacksonException ex) {
            throw new IllegalStateException("Não foi possível serializar a lista de NRs", ex);
        }
    }

    private void checkName(String name) {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 255) {
            throw new BusinessException("O nome do cargo deve ter entre 2 e 255 caracteres");
        }
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll(String uid, AuthenticatedUser user) {
        checkIdentity(uid, user);
        return managementRepository.findAll(uid);
    }

    @Transactional(readOnly = true)
    public List<RoleNrResponse> findNrs(String uid, Long roleId, AuthenticatedUser user) {
        checkIdentity(uid, user);
        checkRoleId(roleId);
        return managementRepository.findNrs(uid, roleId);
    }

    @Transactional(readOnly = true)
    public List<RoleNrOptionResponse> findNrOptions(String uid, Long roleId, AuthenticatedUser user) {
        List<RoleNrResponse> linked = findNrs(uid, roleId, user);
        List<NrDocument> documents = nrDocuments.findByUsability("Colaborador");
        Set<Integer> codes = documents.stream().map(NrDocument::getId).collect(Collectors.toSet());
        linked.stream().map(RoleNrResponse::nrCode).forEach(codes::add);
        return nrMapper.toOptions(documents, linked, findPersistedNrCodes(codes));
    }

    private Set<Integer> findPersistedNrCodes(Collection<Integer> codes) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        return nrCatalog.findAllById(codes).stream().map(NrCatalog::getCodeNr).collect(Collectors.toSet());
    }

    private void checkIdentity(String uid, AuthenticatedUser user) {
        if (uid == null || uid.isBlank()) {
            throw new BusinessException("firebase_uid é obrigatório");
        }
        if (user == null || !uid.equals(user.firebaseUid())) {
            throw new AccessDeniedException("firebase_uid deve corresponder ao usuário autenticado");
        }
    }

    private void checkRoleId(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new BusinessException("id_cargo deve ser positivo");
        }
    }

    public Map<String, Role> createForWorkspace(Workspace workspace, Collection<String> names) {
        Map<String, String> namesByNormalizedValue = new LinkedHashMap<>();
        names.stream()
                .filter(this::hasText)
                .forEach(name -> namesByNormalizedValue.putIfAbsent(NormalizedText.key(name), name.trim()));

        List<Role> roles = namesByNormalizedValue.values().stream()
                .map(name -> create(workspace, name))
                .toList();
        List<Role> savedRoles = roleRepository.saveAll(roles);

        Map<String, Role> rolesByName = new LinkedHashMap<>();
        savedRoles.forEach(role -> rolesByName.put(NormalizedText.key(role.getName()), role));
        return rolesByName;
    }

    @Transactional(readOnly = true)
    public Map<String, Role> resolveForImport(Workspace workspace, Collection<String> names) {
        Map<String, Role> rolesByName = new LinkedHashMap<>();
        List<Role> existingRoles = roleRepository.findByWorkspace(workspace);
        for (Role role : existingRoles) {
            if (rolesByName.putIfAbsent(NormalizedText.key(role.getName()), role) != null) {
                throw new ConflictException("Existem cargos com nomes ambíguos no workspace");
            }
        }
        names.stream().filter(this::hasText).forEach(name ->
                rolesByName.computeIfAbsent(NormalizedText.key(name), key -> create(workspace, name.trim())));
        return rolesByName;
    }

    @Transactional
    public int persistNewForImport(Collection<Role> roles) {
        List<Role> newRoles = roles.stream()
                .filter(role -> role.getId() == null)
                .distinct()
                .toList();
        for (Role role : newRoles) {
            checkName(role.getName());
        }
        if (!newRoles.isEmpty()) {
            roleRepository.saveAll(newRoles);
        }
        return newRoles.size();
    }

    private Role create(Workspace workspace, String name) {
        Role role = new Role();
        role.setName(name);
        role.setActive(true);
        role.setWorkspace(workspace);
        return role;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
