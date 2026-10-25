package com.astro.api.form.mapper;

import com.astro.api.form.model.FormStatus;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;

public class FormStatusConverter implements MongoValueConverter<Object, String> {
    @Override
    public FormStatus read(String value, MongoConversionContext context) {
        return FormStatus.fromValue(value);
    }

    @Override
    public String write(Object value, MongoConversionContext context) {
        if (value instanceof FormStatus status) return status.value();
        return FormStatus.fromValue((String) value).value();
    }
}
