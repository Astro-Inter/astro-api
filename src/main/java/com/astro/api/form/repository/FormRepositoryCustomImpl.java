package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.model.FormScope;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.bson.Document;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class FormRepositoryCustomImpl implements FormRepositoryCustom {
    private final MongoTemplate mongo;

    public FormRepositoryCustomImpl(MongoTemplate mongo) { this.mongo = mongo; }

    public Optional<Form> update(Form form, FormScope scope, Long expectedVersion) {
        Update update = new Update().set("titulo", form.getTitle()).set("descricao", form.getDescription())
                .set("id_gestor", form.getManagerId()).set("nr", form.getNrCode()).set("id_unidade", form.getUnitId())
                .set("data_limite", form.getDueDate()).set("perguntas", form.getQuestions())
                .set("atualizado_em", form.getUpdatedAt()).set("versao", expectedVersion == null ? 1L : expectedVersion + 1);
        return Optional.ofNullable(mongo.findAndModify(editableQuery(form, scope, expectedVersion), update,
                FindAndModifyOptions.options().returnNew(true), Form.class));
    }

    public boolean delete(Form form, FormScope scope) {
        return mongo.remove(editableQuery(form, scope, form.getVersion()), Form.class).getDeletedCount() == 1;
    }

    public Optional<Form> publish(Form form, FormScope scope) {
        Query query = editableQuery(form, scope, form.getVersion()).addCriteria(Criteria.where("status").is(FormStatus.DRAFT));
        Update update = new Update().set("status", FormStatus.PENDING).set("atualizado_em", form.getUpdatedAt())
                .set("versao", form.getVersion() == null ? 1L : form.getVersion() + 1);
        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Form.class));
    }

    private Query editableQuery(Form form, FormScope scope, Long expectedVersion) {
        Criteria permissions = Criteria.where("id_workspace").is(scope.workspaceId());
        if (!scope.workspaceManager()) permissions = new Criteria().andOperator(permissions,
                Criteria.where("id_gestor_remetente").is(scope.managerId()));
        return Query.query(new Criteria().andOperator(permissions, Criteria.where("_id").is(form.getId()),
                Criteria.where("versao").is(expectedVersion), Criteria.where("status").is(form.getStatus()),
                Criteria.where("status").in(FormStatus.DRAFT, FormStatus.PENDING),
                Criteria.where("respondido_em").is(null),
                Criteria.where("perguntas").not().elemMatch(Criteria.where("resposta.0").exists(true))));
    }

    public long nextId() {
        // Bootstrap above existing numeric IDs; $max never moves a concurrent counter backwards.
        Query highestQuery = new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1);
        highestQuery.fields().include("_id");
        Form highest = mongo.findOne(highestQuery, Form.class);
        long floor = highest == null ? 0 : highest.getId();
        Query sequence = Query.query(Criteria.where("_id").is("forms"));
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

    public Optional<Form> findById(Long id, FormScope scope) {
        return Optional.ofNullable(mongo.findOne(Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(id), readable(scope))), Form.class));
    }

    public Listing findAll(FormScope scope, String search, FormStatus status, int page, int size) {
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

    private Criteria readable(FormScope scope) {
        Criteria workspace = Criteria.where("id_workspace").is(scope.workspaceId());
        if (scope.workspaceManager()) return workspace;
        return new Criteria().andOperator(workspace, new Criteria().orOperator(
                Criteria.where("id_gestor_remetente").is(scope.managerId()),
                new Criteria().andOperator(Criteria.where("id_gestor").is(scope.managerId()),
                        Criteria.where("status").in(FormStatus.PENDING, FormStatus.COMPLETED))));
    }

}
