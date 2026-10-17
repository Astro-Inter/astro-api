package com.astro.api.user.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.mapper.AccountMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.repository.AccountRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.unit.model.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
public class AccountService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountService.class);
    private final UserRepository users;
    private final UserAvatarService avatars;
    private final AccountMapper mapper;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final UnitRepository units;
    private final FirebaseIdentityService identities;
    private final TransactionTemplate transactions;

    public AccountService(UserRepository users, UserAvatarService avatars, AccountMapper mapper,
                          AccountRepository accounts, RoleRepository roles, UnitRepository units,
                          FirebaseIdentityService identities, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.avatars = avatars;
        this.mapper = mapper;
        this.accounts = accounts;
        this.roles = roles;
        this.units = units;
        this.identities = identities;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public AccountSummaryResponse findSummary(AuthenticatedUser principal) {
        User user = requireAccount(principal);
        return mapper.toSummary(user, avatars.findUrl(user.getId()));
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse findDetails(AuthenticatedUser principal) {
        User user = requireAccount(principal);
        return mapper.toDetails(user, avatars.findUrl(user.getId()));
    }

    public AccountDetailsResponse update(UpdateAccountDetailsRequest body, AuthenticatedUser principal) {
        requirePrincipal(principal);
        EmailChange[] emailChange = new EmailChange[1];
        try {
            // The template includes commit: a failed commit must compensate Firebase too.
            return transactions.execute(transaction -> {
                User user = accounts.findForUpdateByFirebaseUid(principal.firebaseUid())
                        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
                validateAccount(user, principal);
                Long workspaceId = user.getUnit().workspace.id;
                boolean roleChanged = user.getRole() == null || !Objects.equals(body.roleId(), user.getRole().getId());
                boolean unitChanged = !Objects.equals(body.unitId(), user.getUnit().id);
                if (principal.role() == Role.GESTOR && (roleChanged || unitChanged)) {
                    throw new AccessDeniedException("A alteração do próprio cargo ou unidade exige um gestor de workspace");
                }
                com.astro.api.role.model.Role role = roles.findById(body.roleId())
                        .filter(value -> value.getWorkspace() != null && workspaceId.equals(value.getWorkspace().id))
                        .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
                Unit unit = units.findById(body.unitId())
                        .filter(value -> value.workspace != null && workspaceId.equals(value.workspace.id))
                        .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
                if (roleChanged && !Boolean.TRUE.equals(accounts.lockRoleActive(body.roleId(), workspaceId))) {
                    throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
                }
                if (unitChanged && !Boolean.TRUE.equals(accounts.lockUnitActive(body.unitId(), workspaceId))) {
                    throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
                }
                if (accounts.cpfExists(body.cpf(), user.getId())) throw new ConflictException("Já existe um usuário com este CPF");
                if (accounts.emailExists(body.email(), user.getId())) throw new ConflictException("Já existe um usuário com este e-mail");
                String previousEmail = user.getEmail();
                // Generate the optional photo before changing the identity in either system.
                String profilePhotoUrl = avatars.findUrl(user.getId());
                mapper.applyUpdate(user, body, role, unit);
                User saved = accounts.saveAndFlush(user);
                if (!body.email().equalsIgnoreCase(previousEmail)) {
                    FirebaseIdentityService.EmailState previous = identities.readEmailState(user.getFirebaseUid());
                    if (previous.email() == null || previousEmail == null || !previousEmail.equalsIgnoreCase(previous.email())) {
                        throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da edição");
                    }
                    EmailChange change = new EmailChange(user.getId(), user.getFirebaseUid(), previous);
                    try {
                        identities.updateEmail(user.getFirebaseUid(), body.email());
                    } catch (ConflictException conflict) {
                        // Firebase explicitly rejected the duplicate; its identity was not changed.
                        throw conflict;
                    } catch (RuntimeException failure) {
                        // A timeout can occur after Firebase applied the change.
                        emailChange[0] = change;
                        throw failure;
                    }
                    emailChange[0] = change;
                }
                return mapper.toDetails(saved, profilePhotoUrl);
            });
        } catch (RuntimeException failure) {
            if (emailChange[0] != null) compensateEmail(emailChange[0], failure);
            throw failure;
        }
    }

    private void compensateEmail(EmailChange change, RuntimeException original) {
        try {
            identities.restoreEmail(change.firebaseUid(), change.previous());
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
            try {
                transactions.executeWithoutResult(transaction -> {
                    if (accounts.deactivateAfterEmailFailure(change.userId(), change.firebaseUid()) != 1) {
                        throw new IllegalStateException("Conta não encontrada para contenção da falha");
                    }
                });
            } catch (RuntimeException containmentFailure) {
                original.addSuppressed(containmentFailure);
            }
            LOGGER.error("Falha na compensação de e-mail da conta {}; reconciliação operacional necessária", change.userId(), original);
            throw new IllegalStateException("Falha de sincronização da autenticação da conta", original);
        }
    }

    private record EmailChange(Long userId, String firebaseUid, FirebaseIdentityService.EmailState previous) { }

    private User requireAccount(AuthenticatedUser principal) {
        requirePrincipal(principal);
        User user = users.findByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        validateAccount(user, principal);
        return user;
    }

    private void requirePrincipal(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null || principal.firebaseUid().isBlank()
                || (principal.role() != Role.GESTOR && principal.role() != Role.GESTOR_WORKSPACE)) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
    }

    private void validateAccount(User user, AuthenticatedUser principal) {
        if (user.getStatus() != UserStatus.ATIVO || user.getType() == null
                || !user.getType().name().equals(principal.role().name())
                || user.getUnit() == null || user.getUnit().workspace == null
                || user.getUnit().workspace.id == null) {
            throw new AccessDeniedException("Gestor ativo com workspace necessário");
        }
        if (user.getRole() != null && (user.getRole().getWorkspace() == null
                || !user.getUnit().workspace.id.equals(user.getRole().getWorkspace().id))) {
            throw new AccessDeniedException("Cargo fora do workspace da conta");
        }
    }
}
