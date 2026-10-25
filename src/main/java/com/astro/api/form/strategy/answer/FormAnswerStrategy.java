package com.astro.api.form.strategy.answer;

import com.astro.api.form.dto.request.FormQuestionAnswerRequest;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormQuestionType;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface FormAnswerStrategy {
    FormQuestionType supportedType();
    List<Object> validateAndNormalize(Form.FormQuestion question, FormQuestionAnswerRequest answer,
                                      List<MultipartFile> files);
}
