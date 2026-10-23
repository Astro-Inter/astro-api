package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormReadFilter;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.model.FormWriteCondition;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class FormRepositoryCustomImpl implements FormRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    public FormRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Form> update(Form form, FormWriteCondition writeCondition) {
        Update update = new Update().set("titulo", form.getTitle()).set("descricao", form.getDescription())
                .set("managerId", form.getManagerId()).set("nr", form.getNrCode()).set("unitId", form.getUnitId())
                .set("data_limite", form.getDueDate()).set("perguntas", form.getQuestions())
                .set("atualizado_em", form.getUpdatedAt()).set("version", nextVersion(writeCondition));
        return Optional.ofNullable(mongoTemplate.findAndModify(unansweredQuery(writeCondition), update,
                FindAndModifyOptions.options().returnNew(true), Form.class));
    }

    @Override
    public boolean deleteIfUnanswered(FormWriteCondition writeCondition) {
        return mongoTemplate.remove(unansweredQuery(writeCondition), Form.class).getDeletedCount() == 1;
    }

    @Override
    public Optional<Form> updateStatus(FormWriteCondition writeCondition, FormStatus targetStatus, Instant updatedAt) {
        Update update = new Update().set("status", targetStatus).set("atualizado_em", updatedAt)
                .set("version", nextVersion(writeCondition));
        return Optional.ofNullable(mongoTemplate.findAndModify(unansweredQuery(writeCondition), update,
                FindAndModifyOptions.options().returnNew(true), Form.class));
    }

    private long nextVersion(FormWriteCondition writeCondition) {
        return writeCondition.expectedVersion() == null ? 1L : writeCondition.expectedVersion() + 1;
    }

    private Query unansweredQuery(FormWriteCondition writeCondition) {
        Criteria accessCriteria = Criteria.where("id_workspace").is(writeCondition.workspaceId());
        if (writeCondition.senderManagerId() != null) {
            accessCriteria = new Criteria().andOperator(accessCriteria,
                    Criteria.where("id_gestor_remetente").is(writeCondition.senderManagerId()));
        }
        return Query.query(new Criteria().andOperator(accessCriteria,
                Criteria.where("_id").is(writeCondition.formId()),
                Criteria.where("versao").is(writeCondition.expectedVersion()),
                Criteria.where("status").is(writeCondition.expectedStatus()),
                Criteria.where("respondido_em").is(null),
                Criteria.where("perguntas").not().elemMatch(Criteria.where("resposta.0").exists(true))));
    }

    @Override
    public Optional<Form> findById(String formId, FormReadFilter readFilter) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(formId), readCriteria(readFilter)));
        return Optional.ofNullable(mongoTemplate.findOne(query, Form.class));
    }

    @Override
    public Listing findAll(FormReadFilter readFilter, String search, FormStatus status, int page, int size) {
        Criteria criteria = readCriteria(readFilter);
        if (status != null) criteria = new Criteria().andOperator(criteria, Criteria.where("status").is(status));
        if (search != null && !search.isEmpty()) {
            Pattern literal = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("titulo").regex(literal), Criteria.where("descricao").regex(literal)));
        }
        long total = mongoTemplate.count(Query.query(criteria), Form.class);
        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("criado_em"), Sort.Order.desc("_id")))
                .skip((long) page * size).limit(size);
        query.fields().include("_id", "titulo", "descricao", "status", "perguntas.id_pergunta");
        return new Listing(mongoTemplate.find(query, Form.class), total);
    }

    private Criteria readCriteria(FormReadFilter readFilter) {
        Criteria workspaceCriteria = Criteria.where("id_workspace").is(readFilter.workspaceId());
        List<Criteria> participantCriteria = new ArrayList<>();
        if (readFilter.senderManagerId() != null) {
            participantCriteria.add(Criteria.where("id_gestor_remetente").is(readFilter.senderManagerId()));
        }
        if (readFilter.recipientManagerId() != null) {
            participantCriteria.add(new Criteria().andOperator(
                    Criteria.where("id_gestor").is(readFilter.recipientManagerId()),
                    Criteria.where("status").in(readFilter.recipientStatuses())));
        }
        return participantCriteria.isEmpty() ? workspaceCriteria
                : new Criteria().andOperator(workspaceCriteria, new Criteria().orOperator(participantCriteria));
    }
}
