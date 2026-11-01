package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.mapper.ChatContactMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.ChatContactProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.workspace.model.Workspace;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatContactServiceTest {
    private ChatContactService service;
    private User currentUser;
    private List<UserType> queriedTypes;
    private UserAvatarService userAvatarService;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.id = 47L;
        Unit unit = new Unit();
        unit.workspace = workspace;
        unit.isActive = true;
        currentUser = new User();
        currentUser.setId(8L);
        currentUser.setStatus(UserStatus.ATIVO);
        currentUser.setUnit(unit);
        userAvatarService = mock(UserAvatarService.class);
        when(userAvatarService.findUrls(anyCollection())).thenReturn(Map.of());
        service = service(repository(List.of()));
    }

    @Test
    void collaboratorCanOnlySeeManagerTypes() {
        currentUser.setType(UserType.COLABORADOR);
        service = service(repository(List.of(contact(12L, "Ana Lima", UserType.GESTOR))));

        var result = service.findContacts(principal(Role.COLABORADOR));

        assertEquals(1, result.size());
        assertEquals(12L, result.getFirst().id());
        assertEquals(List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE), queriedTypes);
    }

    @Test
    void managersCanSeeAllAndOnlyTheThreeChatUserTypes() {
        for (UserType type : List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE)) {
            currentUser.setType(type);
            service = service(repository(List.of()));

            service.findContacts(principal(Role.valueOf(type.name())));

            assertEquals(List.of(UserType.COLABORADOR, UserType.GESTOR, UserType.GESTOR_WORKSPACE),
                    queriedTypes);
        }
    }

    private UserRepository repository(List<ChatContactProjection> contacts) {
        return (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findByFirebaseUid" -> Optional.of(currentUser);
                    case "findChatContacts" -> {
                        queriedTypes = List.copyOf((List<UserType>) args[3]);
                        yield contacts;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ChatContactService service(UserRepository repository) {
        return new ChatContactService(repository, new ChatContactMapper(), userAvatarService);
    }

    private ChatContactProjection contact(Long id, String name, UserType type) {
        return new ChatContactProjection() {
            public Long getId() { return id; }
            public String getName() { return name; }
            public UserType getRole() { return type; }
        };
    }

    private AuthenticatedUser principal(Role role) {
        return new AuthenticatedUser("firebase-uid", "user@astro.test", role);
    }
}
