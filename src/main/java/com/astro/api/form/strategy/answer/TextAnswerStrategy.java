package com.astro.api.form.strategy.answer;

import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Component
public class TextAnswerStrategy implements FormAnswerStrategy {
    public FormQuestionType supportedType() { return FormQuestionType.TEXT; }
    public List<Object> validateAndNormalize(Form.FormQuestion question, FormQuestionAnswerRequest answer,
                                              List<MultipartFile> files) {
        FormAnswerRules.requireType(answer, supportedType());
        String text = FormAnswerRules.clean(answer.text());
        FormAnswerRules.requireAnswer(question, !text.isEmpty());
        return text.isEmpty() ? List.of() : List.of(text);
    }
}
