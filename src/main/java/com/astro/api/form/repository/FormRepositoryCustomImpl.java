package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormReview;
import com.astro.api.form.model.FormStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class FormRepositoryCustomImpl implements FormRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    public FormRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Form> update(Form form, FormWriteCondition writeCondition) {
        Update update = new Update().set("titulo", form.getTitle()).set("descricao", form.getDescription())
                .set("appearance", form.getAppearance()).set("managerId", form.getManagerId()).set("nr_id", form.getNrCode()).set("unitId", form.getUnitId())
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
        Criteria accessCriteria = Criteria.where("workspace_id").is(writeCondition.workspaceId());
        if (writeCondition.senderManagerId() != null) {
            accessCriteria = new Criteria().andOperator(accessCriteria,
                    Criteria.where("gestor_remetente_id").is(writeCondition.senderManagerId()));
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
        query.fields().include("_id", "titulo", "descricao", "status", "configuracao", "perguntas.pergunta_id", "analise.status");
        return new Listing(mongoTemplate.find(query, Form.class), total);
    }

    @Override
    public Listing findReceived(FormReadFilter readFilter, String search, Integer nrCode,
                                FormStatus status, int page, int size) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(readCriteria(readFilter));
        if (status != null) filters.add(Criteria.where("status").is(status));
        if (nrCode != null) filters.add(Criteria.where("nr_id").is(nrCode));
        if (search != null && !search.isEmpty()) {
            Pattern literal = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filters.add(new Criteria().orOperator(Criteria.where("titulo").regex(literal),
                    Criteria.where("descricao").regex(literal)));
        }
        Criteria criteria = new Criteria().andOperator(filters);
        long total = mongoTemplate.count(Query.query(criteria), Form.class);
        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("criado_em"), Sort.Order.desc("_id")))
                .skip((long) page * size).limit(size);
        query.fields().include("_id", "titulo", "descricao", "nr_id", "unidade_id", "gestor_remetente_id",
                "data_limite", "criado_em", "respondido_em", "status", "analise", "configuracao", "perguntas.pergunta_id");
        return new Listing(mongoTemplate.find(query, Form.class), total);
    }

    @Override
    public Optional<Form> submitResponse(Form form, FormResponseCondition condition) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(condition.formId()),
                Criteria.where("workspace_id").is(condition.workspaceId()),
                Criteria.where("gestor_id").is(condition.recipientManagerId()),
                Criteria.where("versao").is(condition.expectedVersion()),
                Criteria.where("status").is(condition.expectedStatus()),
                Criteria.where("respondido_em").is(null), Criteria.where("analise").is(null),
                Criteria.where("perguntas").not().elemMatch(Criteria.where("resposta.0").exists(true))));
        Update update = new Update().set("questions", form.getQuestions()).set("submissionId", form.getSubmissionId())
                .set("respondedAt", form.getRespondedAt()).set("updatedAt", form.getRespondedAt())
                .set("status", form.getStatus())
                .set("version", condition.expectedVersion() == null ? 1L : condition.expectedVersion() + 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Form.class));
    }

    @Override
    public Optional<Form> review(FormWriteCondition condition, FormReview review) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("_id").is(condition.formId()));
        filters.add(Criteria.where("workspace_id").is(condition.workspaceId()));
        filters.add(Criteria.where("versao").is(condition.expectedVersion()));
        filters.add(Criteria.where("status").is(condition.expectedStatus()));
        filters.add(Criteria.where("respondido_em").ne(null));
        filters.add(Criteria.where("analise").is(null));
        if (condition.senderManagerId() != null) {
            filters.add(Criteria.where("gestor_remetente_id").is(condition.senderManagerId()));
        }
        Update update = new Update().set("review", review).set("updatedAt", review.reviewedAt())
                .set("version", nextVersion(condition));
        return Optional.ofNullable(mongoTemplate.findAndModify(Query.query(new Criteria().andOperator(filters)),
                update, FindAndModifyOptions.options().returnNew(true), Form.class));
    }

    private Criteria readCriteria(FormReadFilter readFilter) {
        Criteria workspaceCriteria = Criteria.where("workspace_id").is(readFilter.workspaceId());
        List<Criteria> participantCriteria = new ArrayList<>();
        if (readFilter.senderManagerId() != null) {
            participantCriteria.add(Criteria.where("gestor_remetente_id").is(readFilter.senderManagerId()));
        }
        if (readFilter.recipientManagerId() != null) {
            participantCriteria.add(new Criteria().andOperator(
                    Criteria.where("gestor_id").is(readFilter.recipientManagerId()),
                    Criteria.where("status").in(readFilter.recipientStatuses())));
        }
        return participantCriteria.isEmpty() ? workspaceCriteria
                : new Criteria().andOperator(workspaceCriteria, new Criteria().orOperator(participantCriteria));
    }
}
