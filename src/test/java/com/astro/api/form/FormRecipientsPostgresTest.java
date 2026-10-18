package com.astro.api.form;

import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.FormRecipientProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FORM_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")
class FormRecipientsPostgresTest {
    private static AnnotationConfigApplicationContext context;

    @BeforeAll static void connect() { context = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void close() { if (context != null) context.close(); }

    @Test void selectsOnlyOtherActiveManagersInActiveUnitsOfTheWorkspaceOrderedByName() {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(transaction -> {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
            Workspace workspace = workspace(entityManager, "Workspace");
            Unit activeUnit = unit(entityManager, workspace, "Sede", true);
            Unit otherActiveUnit = unit(entityManager, workspace, "Filial", true);
            Unit inactiveUnit = unit(entityManager, workspace, "Inativa", false);
            Unit foreignUnit = unit(entityManager, workspace(entityManager, "Outro"), "Externa", true);
            User currentManager = user(entityManager, activeUnit, "Atual", UserType.GESTOR, UserStatus.ATIVO);
            User bruno = user(entityManager, activeUnit, "Bruno", UserType.GESTOR, UserStatus.ATIVO);
            User ana = user(entityManager, otherActiveUnit, "Ana", UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
            user(entityManager, activeUnit, "Colaborador", UserType.COLABORADOR, UserStatus.ATIVO);
            user(entityManager, activeUnit, "Desativado", UserType.GESTOR, UserStatus.DESATIVADO);
            user(entityManager, activeUnit, "Pré-cadastrado", UserType.GESTOR_WORKSPACE, UserStatus.PRE_CADASTRADO);
            user(entityManager, inactiveUnit, "Unidade inativa", UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
            user(entityManager, foreignUnit, "Outro workspace", UserType.GESTOR, UserStatus.ATIVO);
            user(entityManager, null, "Sem unidade", UserType.GESTOR, UserStatus.ATIVO);
            entityManager.flush();
            List<FormRecipientProjection> recipients = context.getBean(UserRepository.class).findFormRecipients(
                    workspace.id, currentManager.getId(), UserStatus.ATIVO, List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE));
            assertEquals(List.of(ana.getId(), bruno.getId()), recipients.stream().map(FormRecipientProjection::getId).toList());
            assertEquals(List.of("Ana", "Bruno"), recipients.stream().map(FormRecipientProjection::getName).toList());
            assertEquals(List.of("Filial", "Sede"), recipients.stream().map(FormRecipientProjection::getUnitName).toList());
            transaction.setRollbackOnly();
        });
    }

    @Test void workspaceManagerIsExcludedFromTheirOwnDropdownAndCanHaveNoRecipients() {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(transaction -> {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class));
            Workspace workspace = workspace(entityManager, "Único");
            Unit activeUnit = unit(entityManager, workspace, "Sede", true);
            User currentManager = user(entityManager, activeUnit, "Atual", UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
            entityManager.flush();
            assertTrue(context.getBean(UserRepository.class).findFormRecipients(workspace.id, currentManager.getId(),
                    UserStatus.ATIVO, List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE)).isEmpty());
            transaction.setRollbackOnly();
        });
    }

    private static Workspace workspace(EntityManager entityManager, String name) {
        Workspace workspace = new Workspace(); workspace.name = name; workspace.cnpj = "12345678000100";
        entityManager.persist(workspace); return workspace;
    }

    private static Unit unit(EntityManager entityManager, Workspace workspace, String name, boolean active) {
        Unit unit = new Unit(); unit.name = name; unit.workspace = workspace; unit.isActive = active;
        entityManager.persist(unit); return unit;
    }

    private static User user(EntityManager entityManager, Unit unit, String name, UserType type, UserStatus status) {
        User user = new User(); user.setName(name); user.setEmail(name + "@astro.test");
        user.setUnit(unit); user.setType(type); user.setStatus(status);
        entityManager.persist(user); return user;
    }

    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            String databaseUrl = System.getenv("FORM_TEST_JDBC_URL");
            if (!databaseUrl.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")) {
                throw new IllegalArgumentException("Tests require a local disposable forms_recipients_test database");
            }
            return new DriverManagerDataSource(databaseUrl, "postgres", "forms-recipients-isolated");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource); factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
