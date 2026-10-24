package com.astro.api.form.strategy.answer;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Component
public class PhotoAnswerStrategy implements FormAnswerStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.PHOTO; }
    public List<Object> validateAndNormalize(Form.FormQuestion question, FormQuestionAnswerRequest answer,
                                              List<MultipartFile> files) {
        FormAnswerRules.requireType(answer, supportedType());
        List<Integer> indexes = answer.fileIndexes() == null ? List.of() : answer.fileIndexes();
        FormAnswerRules.requireAnswer(question, !indexes.isEmpty());
        int maxFiles = question.getMaxFiles() == null ? 1 : question.getMaxFiles();
        int maxSizeMb = question.getMaxFileSizeMb() == null ? 10 : question.getMaxFileSizeMb();
        if (indexes.size() > maxFiles || new HashSet<>(indexes).size() != indexes.size()) {
            throw new BusinessException("Respeite o limite de fotos da pergunta, sem repetir arquivos");
        }
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= files.size()) {
                throw new BusinessException("Índice de foto inválido");
            }
            MultipartFile file = files.get(index);
            if (file == null || file.isEmpty() || file.getSize() > Math.min(maxSizeMb, 50) * 1024L * 1024) {
                throw new BusinessException("Foto vazia ou acima do limite da pergunta (máximo de 50 MB por arquivo)");
            }
        }
        return new ArrayList<>(indexes);
    }
}
