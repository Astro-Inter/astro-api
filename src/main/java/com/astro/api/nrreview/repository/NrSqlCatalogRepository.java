package com.astro.api.nrreview.repository;

import java.util.function.Supplier;
import java.sql.Types;
import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class NrSqlCatalogRepository {
    private final EntityManager entityManager;
    private final SimpleJdbcCall catalogLock;
    private final TransactionTemplate transactionTemplate;

    public NrSqlCatalogRepository(EntityManager entityManager, JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.catalogLock = new SimpleJdbcCall(jdbcTemplate)
                .withFunctionName("pg_advisory_xact_lock")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlOutParameter("return", Types.OTHER),
                        new SqlParameter("namespace", Types.INTEGER), new SqlParameter("nrId", Types.INTEGER));
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(10);
    }

    public void synchronizeLocked(int nrId, Supplier<String> payloadSupplier) {
        transactionTemplate.executeWithoutResult(status -> {
            catalogLock.execute(Map.of("namespace", 619, "nrId", nrId));
            String payload = payloadSupplier.get();
            StoredProcedureQuery procedure = entityManager.createStoredProcedureQuery("ler_json_nr");
            procedure.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);
            procedure.setParameter(1, payload);
            procedure.execute();
        });
    }
}
