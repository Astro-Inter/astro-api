package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FormRepository extends MongoRepository<Form, String>, FormRepositoryCustom {
    @Aggregation(pipeline = {
            "{ '$match': { 'gestor_id': ?0, 'status': 'PENDENTE' } }",
            "{ '$addFields': { '_due_date_missing': { '$cond': [ { '$eq': [ { '$ifNull': [ '$data_limite', null ] }, null ] }, 1, 0 ] } } }",
            "{ '$sort': { '_due_date_missing': 1, 'data_limite': 1, '_id': 1 } }",
            "{ '$limit': 3 }",
            "{ '$project': { '_id': 1, 'titulo': 1, 'data_limite': 1 } }"
    })
    List<Form> findTop3PendingByManagerId(Long managerId);
}
