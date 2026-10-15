package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormAccessService.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class FormManagementRepository {
    private final MongoTemplate mongo;

    public FormManagementRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public Optional<Form> findById(Long id, Scope scope) {
        return Optional.ofNullable(mongo.findOne(Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(id), readable(scope))), Form.class));
    }

    public Listing findAll(Scope scope, String search, FormStatus status, int page, int size) {
        Criteria criteria = readable(scope);
        if (status != null) criteria = new Criteria().andOperator(criteria, Criteria.where("status").is(status));
        if (search != null && !search.isBlank()) {
            Pattern literal = Pattern.compile(Pattern.quote(search.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("titulo").regex(literal), Criteria.where("descricao").regex(literal)));
        }
        long total = mongo.count(Query.query(criteria), Form.class);
        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("criado_em"), Sort.Order.desc("_id")))
                .skip((long) page * size).limit(size);
        query.fields().include("_id", "titulo", "descricao", "status", "perguntas.id_pergunta");
        return new Listing(mongo.find(query, Form.class), total);
    }

    private Criteria readable(Scope scope) {
        Criteria workspace = Criteria.where("id_workspace").is(scope.workspaceId());
        if (scope.workspaceManager()) return workspace;
        return new Criteria().andOperator(workspace, new Criteria().orOperator(
                Criteria.where("id_gestor_remetente").is(scope.managerId()),
                new Criteria().andOperator(Criteria.where("id_gestor").is(scope.managerId()),
                        Criteria.where("status").in(FormStatus.PENDENTE, FormStatus.CONCLUIDO))));
    }

    public record Listing(List<Form> forms, long total) {}
}
