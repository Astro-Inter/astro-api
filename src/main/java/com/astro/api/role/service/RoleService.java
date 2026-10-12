package com.astro.api.role.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.role.dto.request.RoleRequest;
import com.astro.api.role.dto.request.UpdateRoleNrsRequest;
import com.astro.api.role.dto.response.RoleNrOptionResponse;
import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleManagementRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final RoleManagementRepository managementRepository;
    private final NrDocumentRepository nrDocuments;
    private final JsonMapper objectMapper;

    public RoleService(RoleRepository roleRepository, RoleManagementRepository managementRepository,
                        NrDocumentRepository nrDocuments, JsonMapper objectMapper) {
        this.roleRepository = roleRepository;
        this.managementRepository = managementRepository;
        this.nrDocuments = nrDocuments;
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
        for (var nr : body.nrs()) {
            if (!codes.add(nr.nr())) {
                throw new BusinessException("A lista não pode repetir códigos de NR");
            }
        }
        try {
            managementRepository.updateNrs(body.firebaseUid(), roleId, objectMapper.writeValueAsString(body.nrs()));
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
        Set<Integer> linked = findNrs(uid, roleId, user).stream()
                .map(RoleNrResponse::nrCode).collect(Collectors.toSet());
        return nrDocuments.findByUsability("Colaborador").stream()
                .map(nr -> new RoleNrOptionResponse(nr.getId(), nr.getDescription(), linked.contains(nr.getId())))
                .sorted(Comparator.comparing(RoleNrOptionResponse::nr))
                .toList();
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
                .forEach(name -> namesByNormalizedValue.putIfAbsent(normalize(name), name.trim()));

        List<Role> cargos = namesByNormalizedValue.values().stream()
                .map(name -> create(workspace, name))
                .toList();
        List<Role> savedRoles = roleRepository.saveAll(cargos);

        Map<String, Role> cargosByName = new LinkedHashMap<>();
        savedRoles.forEach(cargo -> cargosByName.put(normalize(cargo.getName()), cargo));
        return cargosByName;
    }

    private Role create(Workspace workspace, String name) {
        Role cargo = new Role();
        cargo.setName(name);
        cargo.setActive(true);
        cargo.setWorkspace(workspace);
        return cargo;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
