package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FormRepository extends MongoRepository<Form, Long> {
    @Aggregation(pipeline = {
            "{ '$match': { 'id_gestor': ?0, 'perguntas': { '$elemMatch': { 'resposta': { '$size': 0 } } } } }",
            "{ '$addFields': { '_due_date_missing': { '$cond': [ { '$eq': [ { '$ifNull': [ '$data_limite', null ] }, null ] }, 1, 0 ] } } }",
            "{ '$sort': { '_due_date_missing': 1, 'data_limite': 1, '_id': 1 } }",
            "{ '$limit': 3 }",
            "{ '$project': { '_id': 1, 'titulo': 1, 'data_limite': 1 } }"
    })
    List<Form> findTop3PendingByManagerId(Long managerId);
}
