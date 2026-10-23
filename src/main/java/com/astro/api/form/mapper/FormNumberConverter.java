package com.astro.api.form.mapper;

import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;

public class FormNumberConverter implements MongoValueConverter<Long, Number> {
    @Override
    public Long read(Number value, MongoConversionContext context) {
        return value.longValue();
    }

    @Override
    public Number write(Long value, MongoConversionContext context) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return value.intValue();
        }
        return value;
    }
}
