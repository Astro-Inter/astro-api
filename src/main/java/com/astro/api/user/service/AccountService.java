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
    public AccountSummaryResponse findSummary(AuthenticatedUser authenticatedUser) {
        User user = requireAccount(authenticatedUser);
        return accountMapper.toSummary(user, userAvatarService.findUrl(user.getId()));
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse findDetails(AuthenticatedUser authenticatedUser) {
        User user = requireAccount(authenticatedUser);
        return accountMapper.toDetails(user, userAvatarService.findUrl(user.getId()));
    }

    public AccountDetailsResponse update(UpdateAccountDetailsRequest updateAccountDetailsRequest,
                                         AuthenticatedUser authenticatedUser) {
        requirePrincipal(authenticatedUser);
        AtomicReference<EmailChange> emailChangeReference = new AtomicReference<>();
        try {
            // The template includes commit: a failed commit must compensate Firebase too.
            AccountDetailsResponse updatedAccount = transactionTemplate.execute(transaction ->
                    updateAccount(updateAccountDetailsRequest, authenticatedUser, emailChangeReference));
            return updatedAccount;
        } catch (RuntimeException failure) {
            EmailChange emailChange = emailChangeReference.get();
            if (emailChange != null) compensateEmail(emailChange, failure);
            throw failure;
        }
    }

    private AccountDetailsResponse updateAccount(UpdateAccountDetailsRequest updateAccountDetailsRequest,
                                                 AuthenticatedUser authenticatedUser,
                                                 AtomicReference<EmailChange> emailChangeReference) {
        User user = accountRepository.findForUpdateByFirebaseUid(authenticatedUser.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        validateAccount(user, authenticatedUser);
        Long workspaceId = user.getUnit().workspace.id;
        boolean roleChanged = user.getRole() == null || !Objects.equals(updateAccountDetailsRequest.roleId(), user.getRole().getId());
        boolean unitChanged = !Objects.equals(updateAccountDetailsRequest.unitId(), user.getUnit().id);
        if (authenticatedUser.role() == Role.GESTOR && (roleChanged || unitChanged)) {
            throw new AccessDeniedException("A alteração do próprio cargo ou unidade exige um gestor de workspace");
        }
        com.astro.api.role.model.Role role = requireRole(updateAccountDetailsRequest.roleId(), workspaceId, roleChanged);
        Unit unit = requireUnit(updateAccountDetailsRequest.unitId(), workspaceId, unitChanged);
        checkConflicts(updateAccountDetailsRequest, user.getId());
        String previousEmail = user.getEmail();
        // Generate the optional photo before changing the identity in either system.
        String profilePhotoUrl = userAvatarService.findUrl(user.getId());
        accountMapper.applyUpdate(user, updateAccountDetailsRequest, role, unit);
        User updatedUser = accountRepository.saveAndFlush(user);
        synchronizeEmail(updatedUser, previousEmail, emailChangeReference);
        AccountDetailsResponse updatedAccount = accountMapper.toDetails(updatedUser, profilePhotoUrl);
        return updatedAccount;
    }

    private com.astro.api.role.model.Role requireRole(Long roleId, Long workspaceId, boolean roleChanged) {
        com.astro.api.role.model.Role role = roleRepository.findById(roleId)
                .filter(candidateRole -> candidateRole.getWorkspace() != null && workspaceId.equals(candidateRole.getWorkspace().id))
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
        if (roleChanged && !Boolean.TRUE.equals(accountRepository.lockRoleActive(roleId, workspaceId))) {
            throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
        }
        return role;
    }

    private Unit requireUnit(Long unitId, Long workspaceId, boolean unitChanged) {
        Unit unit = unitRepository.findById(unitId)
                .filter(candidateUnit -> candidateUnit.workspace != null && workspaceId.equals(candidateUnit.workspace.id))
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
        if (unitChanged && !Boolean.TRUE.equals(accountRepository.lockUnitActive(unitId, workspaceId))) {
            throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
        }
        return unit;
    }

    private void checkConflicts(UpdateAccountDetailsRequest updateAccountDetailsRequest, Long userId) {
        if (accountRepository.existsByCpfAndIdNot(updateAccountDetailsRequest.cpf(), userId)) {
            throw new ConflictException("Já existe um usuário com este CPF");
        }
        if (accountRepository.existsByEmailIgnoreCaseAndIdNot(updateAccountDetailsRequest.email(), userId)) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
    }

    private void synchronizeEmail(User user, String previousEmail, AtomicReference<EmailChange> emailChangeReference) {
        if (user.getEmail().equalsIgnoreCase(previousEmail)) return;
        FirebaseIdentityService.EmailState previousEmailState = firebaseIdentityService.readEmailState(user.getFirebaseUid());
        if (previousEmailState.email() == null || previousEmail == null || !previousEmail.equalsIgnoreCase(previousEmailState.email())) {
            throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da edição");
        }
        EmailChange emailChange = new EmailChange(user.getId(), user.getFirebaseUid(), previousEmailState);
        try {
            firebaseIdentityService.updateEmail(user.getFirebaseUid(), user.getEmail());
        } catch (ConflictException conflict) {
            // Firebase explicitly rejected the duplicate; its identity was not changed.
            throw conflict;
        } catch (RuntimeException failure) {
            // A timeout can occur after Firebase applied the change.
            emailChangeReference.set(emailChange);
            throw failure;
        }
        emailChangeReference.set(emailChange);
    }

    private void compensateEmail(EmailChange emailChange, RuntimeException originalFailure) {
        try {
            firebaseIdentityService.restoreEmail(emailChange.firebaseUid(), emailChange.previousEmailState());
        } catch (RuntimeException compensationFailure) {
            originalFailure.addSuppressed(compensationFailure);
            try {
                transactionTemplate.executeWithoutResult(transaction -> {
                    if (accountRepository.deactivateAfterEmailFailure(emailChange.userId(), emailChange.firebaseUid()) != 1) {
                        throw new IllegalStateException("Conta não encontrada para contenção da falha");
                    }
                });
            } catch (RuntimeException containmentFailure) {
                originalFailure.addSuppressed(containmentFailure);
            }
            LOGGER.error("Falha na compensação de e-mail da conta {}; reconciliação operacional necessária", emailChange.userId(), originalFailure);
            throw new IllegalStateException("Falha de sincronização da autenticação da conta", originalFailure);
        }
    }

    private User requireAccount(AuthenticatedUser authenticatedUser) {
        requirePrincipal(authenticatedUser);
        User user = userRepository.findByFirebaseUid(authenticatedUser.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        validateAccount(user, authenticatedUser);
        return user;
    }

    private void requirePrincipal(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.firebaseUid() == null || authenticatedUser.firebaseUid().isBlank()
                || (authenticatedUser.role() != Role.GESTOR && authenticatedUser.role() != Role.GESTOR_WORKSPACE)) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
    }

    private void validateAccount(User user, AuthenticatedUser authenticatedUser) {
        if (user.getStatus() != UserStatus.ATIVO || user.getType() == null
                || !user.getType().name().equals(authenticatedUser.role().name())
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
