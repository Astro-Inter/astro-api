package com.astro.api.form.strategy.answer;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Component
public class OptionAnswerStrategy implements FormAnswerStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.OPTION; }
    public List<Object> validateAndNormalize(Form.FormQuestion question, FormQuestionAnswerRequest answer,
                                              List<MultipartFile> files) {
        FormAnswerRules.requireType(answer, supportedType());
        List<Long> selected = answer.optionIds() == null ? List.of() : answer.optionIds();
        FormAnswerRules.requireAnswer(question, !selected.isEmpty());
        if (new HashSet<>(selected).size() != selected.size()
                || (!Boolean.TRUE.equals(question.getMultiple()) && selected.size() > 1)) {
            throw new BusinessException("Selecione alternativas únicas e respeite a escolha simples ou múltipla");
        }
        List<Form.FormOption> options = question.getOptions() == null ? List.of() : question.getOptions();
        boolean selectedOther = false;
        for (Long optionId : selected) {
            Form.FormOption option = options.stream().filter(value -> Objects.equals(value.getId(), optionId))
                    .findFirst().orElseThrow(() -> new BusinessException("Alternativa não pertence à pergunta"));
            selectedOther |= Boolean.TRUE.equals(option.getOther());
        }
        String otherText = FormAnswerRules.clean(answer.otherText());
        if (selectedOther == otherText.isEmpty()) {
            throw new BusinessException("Preencha a descrição somente ao selecionar Outros");
        }
        List<Object> result = new ArrayList<>(selected);
        if (selectedOther) result.add(new FormOtherAnswer(otherText));
        return List.copyOf(result);
    }
}
