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
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AccountService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountService.class);
    private final UserRepository userRepository;
    private final UserAvatarService userAvatarService;
    private final AccountMapper accountMapper;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;
    private final FirebaseIdentityService firebaseIdentityService;
    private final TransactionTemplate transactionTemplate;

    public AccountService(UserRepository userRepository, UserAvatarService userAvatarService, AccountMapper accountMapper,
                          AccountRepository accountRepository, RoleRepository roleRepository, UnitRepository unitRepository,
                          FirebaseIdentityService firebaseIdentityService, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.userAvatarService = userAvatarService;
        this.accountMapper = accountMapper;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.firebaseIdentityService = firebaseIdentityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public AccountSummaryResponse findSummary(AuthenticatedUser principal) {
        User user = requireAccount(principal);
        return accountMapper.toSummary(user, userAvatarService.findUrl(user.getId()));
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse findDetails(AuthenticatedUser principal) {
        User user = requireAccount(principal);
        return accountMapper.toDetails(user, userAvatarService.findUrl(user.getId()));
    }

    public AccountDetailsResponse update(UpdateAccountDetailsRequest body, AuthenticatedUser principal) {
        requirePrincipal(principal);
        AtomicReference<EmailChange> emailChange = new AtomicReference<>();
        try {
            // The template includes commit: a failed commit must compensate Firebase too.
            AccountDetailsResponse updatedAccount = transactionTemplate.execute(transaction ->
                    updateAccount(body, principal, emailChange));
            return updatedAccount;
        } catch (RuntimeException failure) {
            EmailChange change = emailChange.get();
            if (change != null) compensateEmail(change, failure);
            throw failure;
        }
    }

    private AccountDetailsResponse updateAccount(UpdateAccountDetailsRequest body, AuthenticatedUser principal,
                                                 AtomicReference<EmailChange> emailChange) {
        User user = accountRepository.findForUpdateByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        validateAccount(user, principal);
        Long workspaceId = user.getUnit().workspace.id;
        boolean roleChanged = user.getRole() == null || !Objects.equals(body.roleId(), user.getRole().getId());
        boolean unitChanged = !Objects.equals(body.unitId(), user.getUnit().id);
        if (principal.role() == Role.GESTOR && (roleChanged || unitChanged)) {
            throw new AccessDeniedException("A alteração do próprio cargo ou unidade exige um gestor de workspace");
        }
        com.astro.api.role.model.Role role = requireRole(body.roleId(), workspaceId, roleChanged);
        Unit unit = requireUnit(body.unitId(), workspaceId, unitChanged);
        checkConflicts(body, user.getId());
        String previousEmail = user.getEmail();
        // Generate the optional photo before changing the identity in either system.
        String profilePhotoUrl = userAvatarService.findUrl(user.getId());
        accountMapper.applyUpdate(user, body, role, unit);
        User saved = accountRepository.saveAndFlush(user);
        synchronizeEmail(saved, previousEmail, emailChange);
        AccountDetailsResponse updatedAccount = accountMapper.toDetails(saved, profilePhotoUrl);
        return updatedAccount;
    }

    private com.astro.api.role.model.Role requireRole(Long roleId, Long workspaceId, boolean roleChanged) {
        com.astro.api.role.model.Role role = roleRepository.findById(roleId)
                .filter(value -> value.getWorkspace() != null && workspaceId.equals(value.getWorkspace().id))
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
        if (roleChanged && !Boolean.TRUE.equals(accountRepository.lockRoleActive(roleId, workspaceId))) {
            throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
        }
        return role;
    }

    private Unit requireUnit(Long unitId, Long workspaceId, boolean unitChanged) {
        Unit unit = unitRepository.findById(unitId)
                .filter(value -> value.workspace != null && workspaceId.equals(value.workspace.id))
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
        if (unitChanged && !Boolean.TRUE.equals(accountRepository.lockUnitActive(unitId, workspaceId))) {
            throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
        }
        return unit;
    }

    private void checkConflicts(UpdateAccountDetailsRequest body, Long userId) {
        if (accountRepository.existsByCpfAndIdNot(body.cpf(), userId)) {
            throw new ConflictException("Já existe um usuário com este CPF");
        }
        if (accountRepository.existsByEmailIgnoreCaseAndIdNot(body.email(), userId)) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
    }

    private void synchronizeEmail(User user, String previousEmail, AtomicReference<EmailChange> emailChange) {
        if (user.getEmail().equalsIgnoreCase(previousEmail)) return;
        FirebaseIdentityService.EmailState previous = firebaseIdentityService.readEmailState(user.getFirebaseUid());
        if (previous.email() == null || previousEmail == null || !previousEmail.equalsIgnoreCase(previous.email())) {
            throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da edição");
        }
        EmailChange change = new EmailChange(user.getId(), user.getFirebaseUid(), previous);
        try {
            firebaseIdentityService.updateEmail(user.getFirebaseUid(), user.getEmail());
        } catch (ConflictException conflict) {
            // Firebase explicitly rejected the duplicate; its identity was not changed.
            throw conflict;
        } catch (RuntimeException failure) {
            // A timeout can occur after Firebase applied the change.
            emailChange.set(change);
            throw failure;
        }
        emailChange.set(change);
    }

    private void compensateEmail(EmailChange change, RuntimeException original) {
        try {
            firebaseIdentityService.restoreEmail(change.firebaseUid(), change.previous());
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
            try {
                transactionTemplate.executeWithoutResult(transaction -> {
                    if (accountRepository.deactivateAfterEmailFailure(change.userId(), change.firebaseUid()) != 1) {
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

    private User requireAccount(AuthenticatedUser principal) {
        requirePrincipal(principal);
        User user = userRepository.findByFirebaseUid(principal.firebaseUid())
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
