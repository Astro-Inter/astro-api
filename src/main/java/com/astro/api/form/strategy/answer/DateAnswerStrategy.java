package com.astro.api.form.strategy.answer;

import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Component
public class DateAnswerStrategy implements FormAnswerStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.DATE; }
    public List<Object> validateAndNormalize(Form.FormQuestion question, FormQuestionAnswerRequest answer,
                                              List<MultipartFile> files) {
        FormAnswerRules.requireType(answer, supportedType());
        FormAnswerRules.requireAnswer(question, answer.date() != null);
        return answer.date() == null ? List.of() : List.of(answer.date().toString());
    }
}
