package com.astro.api.unit.repository;

import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import java.util.List;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.workspace.model.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "UNIT_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")
class UnitRepositoryPostgresTest {
    private static final String SCHEMA = "unit_lock_test_" + UUID.randomUUID().toString().replace("-", "");
    private static DriverManagerDataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static TransactionTemplate transactionTemplate;
    private static UnitRepository unitRepository;
    private static TestUnits testUnits;

    @BeforeAll
    static void open() {
        String databaseUrl = System.getenv("UNIT_TEST_JDBC_URL");
        if (!databaseUrl.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")) {
            throw new IllegalArgumentException("Unit tests require the local disposable PostgreSQL database");
        }
        dataSource = new DriverManagerDataSource(databaseUrl, "postgres", "forms-recipients-isolated");
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("currentSchema", SCHEMA);
        dataSource.setConnectionProperties(connectionProperties);
        new JdbcTemplate(dataSource).execute("CREATE SCHEMA " + SCHEMA);
        context = new AnnotationConfigApplicationContext(Config.class);
        unitRepository = context.getBean(UnitRepository.class);
        // The production schema owns this native PostgreSQL field; Hibernate does not map POINT to the address entity.
        new JdbcTemplate(dataSource).execute("ALTER TABLE unidade_endereco ADD COLUMN geolocalizacao POINT");
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        testUnits = transactionTemplate.execute(status -> {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            assertNotNull(entityManager);
            Workspace ownWorkspace = workspace(entityManager, "Empresa", "12345678000199");
            Workspace otherWorkspace = workspace(entityManager, "Outra empresa", "98765432000188");
            Unit addressedUnit = unit(entityManager, ownWorkspace, "Matriz");
            Unit legacyUnit = unit(entityManager, ownWorkspace, "Sem endereço");
            UnitAddress address = new UnitAddress();
            address.unit = addressedUnit;
            address.cep = "01234567";
            address.street = "Rua A";
            address.number = "10A";
            address.city = "São Paulo";
            address.neighborhood = "Centro";
            address.state = "SP";
            entityManager.persist(address);
            return new TestUnits(ownWorkspace.id, otherWorkspace.id, addressedUnit.id, legacyUnit.id);
        });
    }

    @AfterAll
    static void close() {
        try {
            if (context != null) context.close();
        } finally {
            if (dataSource != null) new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void restrictsTheDerivedQueryToTheWorkspaceAndLoadsOptionalAddresses() {
        transactionTemplate.executeWithoutResult(status -> {
            Unit addressedUnit = unitRepository.findByIdAndWorkspace_Id(testUnits.addressedUnitId(),
                    testUnits.workspaceId()).orElseThrow();
            assertEquals("10A", addressedUnit.address.number);
            assertTrue(unitRepository.findByIdAndWorkspace_Id(testUnits.addressedUnitId(),
                    testUnits.otherWorkspaceId()).isEmpty());
            Unit legacyUnit = unitRepository.findByIdAndWorkspace_Id(testUnits.legacyUnitId(),
                    testUnits.workspaceId()).orElseThrow();
            assertNull(legacyUnit.address);
        });
    }

    @Test
    void countsOnlyActiveEmployeesOfAllThreeProfilesInTheSameUnit() {
        transactionTemplate.executeWithoutResult(status -> {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            assertNotNull(entityManager);
            Unit countedUnit = entityManager.find(Unit.class, testUnits.addressedUnitId());
            Unit otherUnit = entityManager.find(Unit.class, testUnits.legacyUnitId());
            for (UserType type : UserType.values()) {
                for (UserStatus userStatus : UserStatus.values()) {
                    User employee = new User();
                    employee.setName(type.name());
                    employee.setEmail(type.name() + userStatus.name() + "@example.com");
                    employee.setType(type); employee.setStatus(userStatus); employee.setUnit(countedUnit);
                    entityManager.persist(employee);
                }
                User employeeInOtherUnit = new User();
                employeeInOtherUnit.setEmail(type.name() + "other@example.com");
                employeeInOtherUnit.setType(type); employeeInOtherUnit.setStatus(UserStatus.ATIVO);
                employeeInOtherUnit.setUnit(otherUnit);
                entityManager.persist(employeeInOtherUnit);
            }
            entityManager.flush();
            UserRepository users = context.getBean(UserRepository.class);
            List<UserType> types = List.of(UserType.COLABORADOR, UserType.GESTOR, UserType.GESTOR_WORKSPACE);
            assertEquals(3, users.countByUnit_IdAndTypeInAndStatus(countedUnit.id, types, UserStatus.ATIVO));
            assertEquals(3, users.countByUnit_IdAndTypeInAndStatus(otherUnit.id, types, UserStatus.ATIVO));
            assertEquals(0, users.countByUnit_IdAndTypeInAndStatus(-1L, types, UserStatus.ATIVO));
            status.setRollbackOnly();
        });
    }

    @Test
    void projectsNativePointAsLongitudeThenLatitudeAndScopesTheUnit() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("UPDATE unidade_endereco SET geolocalizacao = point(?, ?) WHERE unidade_id = ?",
                -46.6, -23.5, testUnits.addressedUnitId());
        UnitLocationProjection location = unitRepository.findLocationByIdAndWorkspaceId(testUnits.addressedUnitId(),
                testUnits.workspaceId()).orElseThrow();
        assertEquals(testUnits.addressedUnitId(), location.getUnitId()); assertTrue(location.getActive());
        assertEquals(-23.5, location.getLatitude()); assertEquals(-46.6, location.getLongitude());
        assertTrue(unitRepository.findLocationByIdAndWorkspaceId(testUnits.addressedUnitId(), testUnits.otherWorkspaceId()).isEmpty());
        UnitLocationProjection missingAddress = unitRepository.findLocationByIdAndWorkspaceId(testUnits.legacyUnitId(),
                testUnits.workspaceId()).orElseThrow();
        assertNull(missingAddress.getLatitude()); assertNull(missingAddress.getLongitude());
        jdbcTemplate.update("UPDATE unidade_endereco SET geolocalizacao = NULL WHERE unidade_id = ?", testUnits.addressedUnitId());
        assertNull(unitRepository.findLocationByIdAndWorkspaceId(testUnits.addressedUnitId(), testUnits.workspaceId()).orElseThrow().getLatitude());
    }

    @Test
    void persistsGeocodedCoordinatesInNativePointWithLongitudeFirst() {
        transactionTemplate.executeWithoutResult(status -> {
            UnitAddressRepository addresses = context.getBean(UnitAddressRepository.class);
            assertEquals(1, addresses.updateGeolocation(testUnits.addressedUnitId(), -46.63, -23.55));
            UnitLocationProjection location = unitRepository.findLocationByIdAndWorkspaceId(
                    testUnits.addressedUnitId(), testUnits.workspaceId()).orElseThrow();
            assertEquals(-46.63, location.getLongitude());
            assertEquals(-23.55, location.getLatitude());
            assertEquals(0, addresses.updateGeolocation(-1L, -46.63, -23.55));
            assertEquals(1, addresses.clearGeolocation(testUnits.addressedUnitId()));
            UnitLocationProjection cleared = unitRepository.findLocationByIdAndWorkspaceId(
                    testUnits.addressedUnitId(), testUnits.workspaceId()).orElseThrow();
            assertNull(cleared.getLongitude());
            assertNull(cleared.getLatitude());
            status.setRollbackOnly();
        });
    }

    @Test
    void blocksAnotherConnectionUntilTheUnitTransactionFinishes() throws SQLException {
        transactionTemplate.executeWithoutResult(status -> {
            unitRepository.findByIdAndWorkspace_Id(testUnits.addressedUnitId(), testUnits.workspaceId()).orElseThrow();
            try (Connection competingConnection = dataSource.getConnection();
                 Statement statement = competingConnection.createStatement();
                 PreparedStatement update = competingConnection.prepareStatement(
                         "UPDATE unidade SET nome = ? WHERE id_unidade = ?")) {
                statement.execute("SET lock_timeout = '250ms'");
                update.setString(1, "Alteração concorrente");
                update.setLong(2, testUnits.addressedUnitId());
                SQLException lockFailure = assertThrows(SQLException.class, update::executeUpdate);
                assertEquals("55P03", lockFailure.getSQLState());
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
        try (Connection competingConnection = dataSource.getConnection();
             PreparedStatement update = competingConnection.prepareStatement(
                     "UPDATE unidade SET nome = ? WHERE id_unidade = ?")) {
            update.setString(1, "Alteração após commit");
            update.setLong(2, testUnits.addressedUnitId());
            assertEquals(1, update.executeUpdate());
        }
    }

    private static Workspace workspace(EntityManager entityManager, String name, String cnpj) {
        Workspace workspace = new Workspace();
        workspace.name = name;
        workspace.cnpj = cnpj;
        entityManager.persist(workspace);
        return workspace;
    }

    private static Unit unit(EntityManager entityManager, Workspace workspace, String name) {
        Unit unit = new Unit();
        unit.name = name;
        unit.workspace = workspace;
        unit.isActive = true;
        entityManager.persist(unit);
        return unit;
    }

    private record TestUnits(Long workspaceId, Long otherWorkspaceId, Long addressedUnitId, Long legacyUnitId) { }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UnitRepository.class, UserRepository.class})
    static class Config {
        @Bean DataSource dataSource() { return UnitRepositoryPostgresTest.dataSource; }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.astro.api.unit.model", "com.astro.api.workspace.model",
                    "com.astro.api.user.model", "com.astro.api.role.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.default_schema", SCHEMA,
                    "hibernate.show_sql", "true"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
