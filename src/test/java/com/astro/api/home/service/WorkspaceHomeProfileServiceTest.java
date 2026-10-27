package com.astro.api.home.service;

import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.repository.HomeUserIdProjection;
import com.astro.api.user.service.UserAvatarService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceHomeProfileServiceTest {
    private final HomeRepository repository = mock(HomeRepository.class);
    private final UserAvatarService avatars = mock(UserAvatarService.class);
    private final WorkspaceHomeProfileService service = new WorkspaceHomeProfileService(repository, avatars);

    @Test
    void usesOnlyTheAuthenticatedIdentityAndReturnsItsStoredPhoto() {
        when(repository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(() -> 42L));
        when(avatars.findUrl(42L)).thenReturn("https://images.example.test/own.jpg");
        assertEquals("https://images.example.test/own.jpg", service.findPhotoUrl("own-uid"));
        verify(repository).findByFirebaseUid("own-uid");
        verify(avatars).findUrl(42L);
        verifyNoMoreInteractions(repository, avatars);
    }

    @Test
    void returnsNullForMissingIdentityOrMissingPhoto() {
        assertNull(service.findPhotoUrl("missing"));
        verifyNoInteractions(avatars);
        when(repository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(() -> 42L));
        assertNull(service.findPhotoUrl("own-uid"));
    }

    @Test
    void photoStorageAndDatabaseFailuresDoNotEscape() {
        when(repository.findByFirebaseUid("own-uid")).thenReturn(Optional.of(() -> 42L));
        when(avatars.findUrl(42L)).thenThrow(new IllegalStateException("storage unavailable"));
        assertNull(service.findPhotoUrl("own-uid"));
        when(repository.findByFirebaseUid("own-uid")).thenThrow(new DataAccessResourceFailureException("offline"));
        assertNull(service.findPhotoUrl("own-uid"));
    }

    @Test
    void suspendsTheIndicatorsTransactionWhileReadingTheOptionalPhoto() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        WorkspaceHomeProfileService proxy = (WorkspaceHomeProfileService) factory.getProxy();
        when(repository.findByFirebaseUid("own-uid")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new DataAccessResourceFailureException("optional photo offline");
        });
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertNull(proxy.findPhotoUrl("own-uid"));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(status.isRollbackOnly());
        });
        verify(connection).commit();
        verify(connection, never()).rollback();
    }
}
