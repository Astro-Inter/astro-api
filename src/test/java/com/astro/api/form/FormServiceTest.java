package com.astro.api.form;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.mapper.FormMapper;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.repository.FormManagementRepository;
import com.astro.api.form.repository.FormRepository;
import com.astro.api.form.service.FormAccessService;
import com.astro.api.form.service.FormService;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final FormManagementRepository repository = mock(FormManagementRepository.class);
    final FormRepository homeRepository = mock(FormRepository.class);
    final FormAccessService access = new FormAccessService(users);
    final FormService service = new FormService(homeRepository, repository, access, new FormMapper());
    final AuthenticatedUser principal = new AuthenticatedUser("sender", "sender@astro.com", Role.GESTOR);
    User sender;

    @BeforeEach void setUp() {
        sender = manager(10L, 1L, UserType.GESTOR);
        when(users.findByFirebaseUid("sender")).thenReturn(Optional.of(sender));
    }

    static User manager(long id, long workspaceId, UserType type) {
        Workspace workspace = new Workspace(); workspace.id = workspaceId;
        Unit unit = new Unit(); unit.id = 3L; unit.workspace = workspace; unit.isActive = true;
        User user = new User(); user.setId(id); user.setType(type); user.setStatus(UserStatus.ATIVO); user.setUnit(unit);
        return user;
    }

    @Test void requiresActiveManagerWithValidatedRoleAndActiveUnit() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(null, null, null, 0, 20));
        sender.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal, null, null, 0, 20));
        sender.setStatus(UserStatus.ATIVO); sender.setType(UserType.GESTOR_WORKSPACE);
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal, null, null, 0, 20));
        sender.setType(UserType.GESTOR); sender.getUnit().isActive = false;
        assertThrows(AccessDeniedException.class, () -> service.findAll(principal, null, null, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test void boundsPagingAndSearchBeforeQueryingMongo() {
        assertThrows(BusinessException.class, () -> service.findAll(principal, null, null, -1, 20));
        assertThrows(BusinessException.class, () -> service.findAll(principal, null, null, 0, 0));
        assertThrows(BusinessException.class, () -> service.findAll(principal, null, null, 0, 101));
        assertThrows(BusinessException.class, () -> service.findAll(principal, "a".repeat(241), null, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test void returnsSummaryAndPagingWithoutAnswers() {
        Form form = new Form(); form.setId(1L); form.setTitle("Pesquisa"); form.setStatus(FormStatus.PENDENTE);
        form.setQuestions(List.of(new Form.FormQuestion(), new Form.FormQuestion()));
        var scope = new FormAccessService.Scope(10L, 1L, false);
        when(repository.findAll(scope, "Pesquisa", FormStatus.PENDENTE, 1, 4))
                .thenReturn(new FormManagementRepository.Listing(List.of(form), 9));
        var result = service.findAll(principal, "Pesquisa", FormStatus.PENDENTE, 1, 4);
        assertEquals(3, result.totalPages()); assertEquals(9, result.totalElements());
        assertEquals(2, result.items().getFirst().questionCount());
        assertEquals(FormStatus.PENDENTE, result.items().getFirst().status());
    }
}
