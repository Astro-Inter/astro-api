package com.astro.api.form.mapper;

import com.astro.api.form.model.FormReviewStatus;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;

public class FormReviewStatusConverter implements MongoValueConverter<FormReviewStatus, String> {
    @Override
    public FormReviewStatus read(String value, MongoConversionContext context) {
        return FormReviewStatus.fromStoredValue(value);
    }

    @Override
    public String write(FormReviewStatus value, MongoConversionContext context) {
        return value.storedValue();
    }
}
