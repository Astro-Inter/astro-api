package com.astro.api.nrreview.service;

import com.astro.api.common.exception.ConflictException;
import com.astro.api.nrreview.dto.NrApprovalRequest;
import com.astro.api.nrreview.dto.NrApprovalResult;
import com.astro.api.nrreview.dto.NrSnapshot;
import com.astro.api.nrreview.dto.NrUpdateRequest;
import com.astro.api.nrreview.mapper.NrReviewMapper;
import com.astro.api.nrreview.repository.NrReviewRepository;
import com.astro.api.nrreview.validation.NrReviewValidator;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
public class NrReviewService {
    private final NrReviewRepository nrReviewRepository;
    private final NrReviewMapper nrReviewMapper;
    private final NrReviewValidator nrReviewValidator;
    private final NrSqlCatalogSynchronizer nrSqlCatalogSynchronizer;

    public NrReviewService(NrReviewRepository nrReviewRepository, NrReviewMapper nrReviewMapper,
            NrReviewValidator nrReviewValidator, NrSqlCatalogSynchronizer nrSqlCatalogSynchronizer) {
        this.nrReviewRepository = nrReviewRepository;
        this.nrReviewMapper = nrReviewMapper;
        this.nrReviewValidator = nrReviewValidator;
        this.nrSqlCatalogSynchronizer = nrSqlCatalogSynchronizer;
    }

    public List<NrSnapshot> list(boolean pending) {
        List<Document> documents = nrReviewRepository.findAll(pending);
        List<Object> nrIds = documents.stream().map(document -> document.get("_id")).toList();
        Map<Object, Document> approvedById = pending ? nrReviewRepository.findPublishedByIds(nrIds) : Map.of();
        List<NrSnapshot> snapshots = new ArrayList<>();
        for (Document document : documents) {
            Document approved = approvedById.get(document.get("_id"));
            NrSnapshot snapshot = nrReviewMapper.toSnapshot(document, approved);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    public NrSnapshot get(int nrId, boolean pending) {
        nrReviewValidator.validateId(nrId);
        Document document = nrReviewRepository.findById(nrId, pending);
        nrReviewValidator.requireDocument(document);
        Document approved = pending ? nrReviewRepository.findById(nrId, false) : null;
        return nrReviewMapper.toSnapshot(document, approved);
    }

    public NrSnapshot update(int nrId, NrUpdateRequest request) {
        nrReviewValidator.validateId(nrId);
        return inTransaction(session -> {
            Document current = nrReviewRepository.findById(session, nrId, false);
            nrReviewValidator.requireDocument(current);
            nrReviewValidator.validateRevision(current, request.revision());
            Document updated = nrReviewMapper.applyContent(current, request.content());
            nrReviewValidator.validateDocument(updated);
            nrReviewRepository.savePublished(session, nrId, updated, false);
            return nrReviewMapper.toSnapshot(updated, null);
        });
    }

    public NrApprovalResult approve(int nrId, NrApprovalRequest request) {
        nrReviewValidator.validateId(nrId);
        String jobId = UUID.randomUUID().toString();
        NrSnapshot result = inTransaction(session -> {
            Document candidate = nrReviewRepository.findById(session, nrId, true);
            if (candidate == null) {
                throw new ConflictException("A análise já foi aprovada ou removida. Atualize a listagem.");
            }
            nrReviewValidator.validateRevision(candidate, request.revision());
            Document current = nrReviewRepository.findById(session, nrId, false);
            nrReviewValidator.validateRevision(current, request.approvedRevision());
            Document approved = request.corrections() == null
                    ? new Document(candidate) : nrReviewMapper.applyContent(candidate, request.corrections());
            nrReviewValidator.validateDocument(approved);
            nrReviewRepository.savePublished(session, nrId, approved, true);
            nrReviewRepository.deletePending(session, nrId);
            nrReviewRepository.enqueueSqlSync(session, jobId, nrId);
            return nrReviewMapper.toSnapshot(approved, null);
        });
        boolean sqlSynchronized = nrSqlCatalogSynchronizer.synchronize(jobId);
        return nrReviewMapper.toApprovalResult(result, sqlSynchronized);
    }

    private NrSnapshot inTransaction(Function<ClientSession, NrSnapshot> operation) {
        try {
            return nrReviewRepository.inTransaction(operation);
        } catch (MongoException exception) {
            if (exception.getCode() == 112 || exception.getCode() == 11000) {
                throw new ConflictException("A NR mudou durante a operação. Reabra a revisão.");
            }
            throw exception;
        }
    }
}
