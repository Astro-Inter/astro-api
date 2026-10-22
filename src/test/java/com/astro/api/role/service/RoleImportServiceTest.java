package com.astro.api.role.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformity.repository.NrDocumentRepository;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleManagementRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.workspace.model.Workspace;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleImportServiceTest {
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleService roleService = new RoleService(roleRepository,
            mock(RoleManagementRepository.class), mock(NrDocumentRepository.class), JsonMapper.builder().build());
    private final Workspace workspace = new Workspace();

    @Test
    void savesOnlyDistinctNewRolesTogetherAndPreservesTheirReferences() {
        Role existingRole = newRole("Gestor");
        existingRole.setId(1L);
        Role firstNewRole = newRole("Analista");
        Role secondNewRole = newRole("Técnico");
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Role> newRoles = invocation.getArgument(0);
            for (int index = 0; index < newRoles.size(); index++) {
                newRoles.get(index).setId(10L + index);
            }
            return newRoles;
        });

        int createdRoles = roleService.persistNewForImport(
                List.of(existingRole, firstNewRole, firstNewRole, secondNewRole));

        assertEquals(2, createdRoles);
        assertEquals(1L, existingRole.getId());
        assertEquals(10L, firstNewRole.getId());
        assertEquals(11L, secondNewRole.getId());
        verify(roleRepository).saveAll(List.of(firstNewRole, secondNewRole));
        verify(roleRepository, never()).saveAndFlush(any());
        verify(roleRepository, never()).flush();
    }

    @Test
    void validatesEveryNewRoleBeforeSavingAnyOfThem() {
        Role validRole = newRole("Analista");
        Role invalidRole = newRole("X");

        assertThrows(BusinessException.class,
                () -> roleService.persistNewForImport(List.of(validRole, invalidRole)));

        verifyNoInteractions(roleRepository);
    }

    @Test
    void initialRegistrationAlsoSavesTheNormalizedRoleNamesTogether() {
        when(roleRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Role> rolesByName = roleService.createForWorkspace(workspace,
                List.of(" Analista ", "ÁNALISTA", "Técnico"));

        assertEquals(2, rolesByName.size());
        assertEquals("Analista", rolesByName.get("analista").getName());
        assertSame(workspace, rolesByName.get("tecnico").getWorkspace());
        assertTrue(rolesByName.get("tecnico").isActive());
        verify(roleRepository).saveAll(List.copyOf(rolesByName.values()));
        verify(roleRepository, never()).saveAndFlush(any());
    }

    private Role newRole(String name) {
        Role role = new Role();
        role.setName(name);
        role.setWorkspace(workspace);
        role.setActive(true);
        return role;
    }
}
