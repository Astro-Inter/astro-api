package com.astro.api.form.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.*;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.dto.response.*;
import com.astro.api.form.mapper.*;
import com.astro.api.form.model.*;
import com.astro.api.form.repository.*;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FormResponseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FormResponseService.class);
    private final FormRepository formRepository;
    private final FormAccessService formAccessService;
    private final FormAnswerValidationService formAnswerValidationService;
    private final FormAnswerMapper formAnswerMapper;
    private final FormResponseMapper formResponseMapper;
    private final FormImageStorageService formImageStorageService;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final Validator validator;
    private final FormLocationService formLocationService;

    public FormResponseService(FormRepository formRepository, FormAccessService formAccessService,
            FormAnswerValidationService formAnswerValidationService, FormAnswerMapper formAnswerMapper,
            FormResponseMapper formResponseMapper, FormImageStorageService formImageStorageService,
            UserRepository userRepository, UnitRepository unitRepository, Validator validator, FormLocationService formLocationService) {
        this.formRepository = formRepository;
        this.formAccessService = formAccessService;
        this.formAnswerValidationService = formAnswerValidationService;
        this.formAnswerMapper = formAnswerMapper;
        this.formResponseMapper = formResponseMapper;
        this.formImageStorageService = formImageStorageService;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.validator = validator;
        this.formLocationService = formLocationService;
    }

    public FormReceivedPageResponse findReceived(AuthenticatedUser principal, String search, Integer nrCode,
                                                 FormStatus status, int page, int size) {
        FormScope scope = formAccessService.requireManager(principal);
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 240)
                || (nrCode != null && nrCode <= 0) || status == FormStatus.DRAFT) {
            throw new BusinessException("Use page >= 0, size de 1 a 100, busca até 240 caracteres, NR positiva e status PENDENTE ou CONCLUIDO");
        }
        FormRepositoryCustom.Listing listing = formRepository.findReceived(formAccessService.recipientFilter(scope),
                search == null ? null : search.strip(), nrCode, status, page, size);
        return formResponseMapper.toPage(listing.forms(), page, size, listing.total());
    }

    public FormResponseDetailsResponse findReceivedById(String formId, AuthenticatedUser principal) {
        FormScope scope = formAccessService.requireManager(principal);
        return details(requireForm(formId, formAccessService.recipientFilter(scope)), scope);
    }

    public FormResponseDetailsResponse submit(String formId, FormResponseRequest request, List<MultipartFile> files,
                                               AuthenticatedUser principal) {
        FormScope scope = formAccessService.requireManager(principal);
        Form form = requireForm(formId, formAccessService.recipientFilter(scope));
        if (form.getStatus() != FormStatus.PENDING || form.getRespondedAt() != null || form.getReview() != null
                || form.getQuestions().stream().anyMatch(question -> question.getAnswer() != null && !question.getAnswer().isEmpty())) {
            throw new ConflictException("Este formulário já foi respondido ou não está pendente");
        }
        List<MultipartFile> submittedFiles = files == null ? List.of() : files;
        Instant respondedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<Long, List<Object>> answers = formAnswerValidationService.validate(form, request, submittedFiles, respondedAt);
        FormLocationCheck locationCheck = formLocationService.validate(form, scope, request.locations(), respondedAt);
        FormResponseCondition responseCondition = new FormResponseCondition(form.getId(), scope.workspaceId(),
                scope.managerId(), form.getVersion(), form.getStatus());
        Map<Integer, FormImage> uploadedImages = new LinkedHashMap<>();
        Form saved;
        boolean databaseWriteStarted = false;
        String submissionId = UUID.randomUUID().toString();
        try {
            for (Form.FormQuestion question : form.getQuestions()) {
                if (FormQuestionType.PHOTO.value().equals(question.getType())) {
                    for (Object fileIndex : answers.get(question.getId())) {
                        int index = (Integer) fileIndex;
                        uploadedImages.put(index, formImageStorageService.upload(form.getId(), question.getId(), submittedFiles.get(index)));
                    }
                }
            }
            formAnswerMapper.applyAnswers(form, request, answers, uploadedImages);
            form.setLocationCheck(locationCheck);
            form.setRespondedAt(respondedAt);
            form.setSubmissionId(submissionId);
            form.setStatus(FormStatus.COMPLETED);
            databaseWriteStarted = true;
            saved = formRepository.submitResponse(form, responseCondition).orElseThrow(() -> new ConflictException(
                            "O formulário foi alterado ou respondido; consulte novamente"));
        } catch (RuntimeException failure) {
            if (databaseWriteStarted && !(failure instanceof ConflictException)) {
                // A lost Mongo acknowledgement does not prove failure. Never remove images from a possibly saved response.
                Optional<Form> recovered = Optional.empty();
                try {
                    recovered = formRepository.findById(form.getId(), formAccessService.recipientFilter(scope))
                            .filter(value -> submissionId.equals(value.getSubmissionId()));
                } catch (RuntimeException recoveryFailure) { failure.addSuppressed(recoveryFailure); }
                if (recovered.isPresent()) {
                    saved = recovered.get();
                } else {
                    LOGGER.warn("Resultado do envio {} do formulário {} não confirmado; imagens preservadas para reconciliação",
                            submissionId, form.getId());
                    throw failure;
                }
            } else {
                for (FormImage image : uploadedImages.values()) {
                    try { formImageStorageService.delete(image); }
                    catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                        LOGGER.warn("Falha ao remover imagem {} do envio não persistido do formulário {}", image.id(), form.getId());
                    }
                }
                throw failure;
            }
        }
        return details(saved, scope);
    }

    public FormResponseDetailsResponse findResponses(String formId, AuthenticatedUser principal) {
        FormScope scope = formAccessService.requireManager(principal);
        Form form = requireForm(formId, formAccessService.readFilter(scope));
        requireCompleted(form);
        return details(form, scope);
    }

    public FormResponseDetailsResponse review(String formId, FormReviewRequest request, AuthenticatedUser principal) {
        FormScope scope = formAccessService.requireManager(principal);
        Form form = requireForm(formId, formAccessService.readFilter(scope));
        formAccessService.requireManagement(form, scope);
        requireCompleted(form);
        if (form.getReview() != null) throw new ConflictException("Este formulário já possui uma análise definitiva");
        if (request == null || !validator.validate(request).isEmpty()) throw new BusinessException("Informe uma análise válida");
        String description = request.description() == null ? null : request.description().strip();
        if (request.status() == FormReviewStatus.REJECTED && (description == null || description.isEmpty())) {
            throw new BusinessException("A reprovação exige uma descrição");
        }
        if (request.status() == FormReviewStatus.APPROVED && description != null) {
            throw new BusinessException("A descrição só deve ser enviada na reprovação");
        }
        FormReview review = new FormReview(request.status(), scope.managerId(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS), description);
        Form saved = formRepository.review(formAccessService.writeCondition(form, scope), review)
                .orElseThrow(() -> new ConflictException("O formulário foi alterado ou já analisado; consulte novamente"));
        return details(saved, scope);
    }

    public FormImageDownloadResponse image(String formId, String imageId, AuthenticatedUser principal) {
        FormScope scope = formAccessService.requireManager(principal);
        Form form = requireForm(formId, formAccessService.readFilter(scope));
        FormImage image = formAnswerMapper.images(form).stream().filter(value -> value.id().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Imagem não encontrada neste formulário"));
        return formImageStorageService.download(image);
    }

    private void requireCompleted(Form form) {
        if (form.getStatus() != FormStatus.COMPLETED || form.getRespondedAt() == null) {
            throw new ConflictException("O formulário ainda não possui um envio de respostas concluído");
        }
    }

    private Form requireForm(String formId, FormReadFilter filter) {
        try {
            if (formId == null || formId.length() != 36) throw new IllegalArgumentException();
            formId = UUID.fromString(formId).toString();
        } catch (IllegalArgumentException exception) { throw new BusinessException("O identificador do formulário deve ser um UUID válido"); }
        return formRepository.findById(formId, filter).orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));
    }

    private FormResponseDetailsResponse details(Form form, FormScope scope) {
        boolean canReview = form.getStatus() == FormStatus.COMPLETED && form.getRespondedAt() != null && form.getReview() == null
                && (scope.workspaceManager() || scope.managerId().equals(form.getSenderManagerId()));
        String unitName = form.getUnitId() == null ? "" : unitRepository.findById(form.getUnitId())
                .filter(unit -> unit.workspace != null && Objects.equals(unit.workspace.id, scope.workspaceId()))
                .map(unit -> unit.name).orElse("");
        String senderName = userName(form.getSenderManagerId());
        String recipientName = userName(form.getManagerId());
        String reviewerName = form.getReview() == null ? null : userName(form.getReview().reviewerManagerId());
        FormLocationPolicy locationPolicy = formLocationService.policy(form);
        return formResponseMapper.toDetails(form, senderName, recipientName, unitName, reviewerName, canReview, locationPolicy);
    }

    private String userName(Long userId) {
        return userId == null ? "" : userRepository.findById(userId).map(User::getName).orElse("");
    }
}
