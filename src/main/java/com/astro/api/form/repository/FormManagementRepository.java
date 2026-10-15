package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormAccessService.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.bson.Document;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class FormManagementRepository {
    private final MongoTemplate mongo;

    public FormManagementRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public Form insert(Form form) { return mongo.insert(form); }

    public long nextId() {
        // Bootstrap above existing numeric IDs; $max never moves a concurrent counter backwards.
        Query highestQuery = new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1);
        highestQuery.fields().include("_id");
        Form highest = mongo.findOne(highestQuery, Form.class);
        long floor = highest == null ? 0 : highest.getId();
        Query sequence = Query.query(Criteria.where("_id").is("formularios"));
        Update initialize = new Update().max("valor", floor);
        try {
            mongo.upsert(sequence, initialize, "sequencias");
        } catch (DuplicateKeyException concurrentInitialization) {
            mongo.updateFirst(sequence, initialize, "sequencias");
        }
        Document counter = mongo.findAndModify(sequence, new Update().inc("valor", 1L),
                FindAndModifyOptions.options().returnNew(true), Document.class, "sequencias");
        if (counter == null) throw new IllegalStateException("Sequência de formulários indisponível");
        return ((Number) counter.get("valor")).longValue();
    }

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
