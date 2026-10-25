package com.astro.api.form.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.model.*;
import com.astro.api.form.strategy.answer.FormAnswerStrategy;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class FormAnswerValidationService {
    private final Map<FormQuestionType, FormAnswerStrategy> answerStrategies;
    private final Validator validator;

    public FormAnswerValidationService(List<FormAnswerStrategy> strategies, Validator validator) {
        this.validator = validator;
        this.answerStrategies = new EnumMap<>(FormQuestionType.class);
        for (FormAnswerStrategy strategy : strategies) answerStrategies.put(strategy.supportedType(), strategy);
    }

    public Map<Long, List<Object>> validate(Form form, FormResponseRequest request, List<MultipartFile> files) {
        if (request == null) throw new BusinessException("Informe as respostas do formulário");
        List<String> violations = validator.validate(request).stream().map(value -> value.getMessage()).sorted().toList();
        if (!violations.isEmpty()) throw new BusinessException(String.join("; ", violations));
        Map<Long, FormQuestionAnswerRequest> suppliedAnswers = new HashMap<>();
        Set<Long> questionIds = new HashSet<>();
        for (Form.FormQuestion question : form.getQuestions()) questionIds.add(question.getId());
        for (FormQuestionAnswerRequest answer : request.answers()) {
            if (!questionIds.contains(answer.questionId()) || suppliedAnswers.putIfAbsent(answer.questionId(), answer) != null) {
                throw new BusinessException("Pergunta desconhecida ou repetida nas respostas");
            }
        }
        Map<Long, List<Object>> normalizedAnswers = new LinkedHashMap<>();
        Set<Integer> usedFiles = new HashSet<>();
        for (Form.FormQuestion question : form.getQuestions()) {
            FormQuestionAnswerRequest answer = suppliedAnswers.getOrDefault(question.getId(),
                    new FormQuestionAnswerRequest(question.getId(), null, null, null, null, null));
            FormQuestionType type = FormQuestionType.fromValue(question.getType());
            List<Object> normalizedAnswer = answerStrategies.get(type).validateAndNormalize(question, answer, files);
            if (type == FormQuestionType.PHOTO) {
                for (Object index : normalizedAnswer) {
                    if (!usedFiles.add((Integer) index)) throw new BusinessException("Cada foto deve pertencer a uma única pergunta");
                }
            }
            normalizedAnswers.put(question.getId(), normalizedAnswer);
        }
        if (usedFiles.size() != files.size()) throw new BusinessException("Há arquivos sem vínculo com uma pergunta de foto");
        return normalizedAnswers;
    }
}
