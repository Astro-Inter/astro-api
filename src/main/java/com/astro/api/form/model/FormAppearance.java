package com.astro.api.form.model;

import org.springframework.data.mongodb.core.mapping.Field;

public record FormAppearance(@Field("icone") String icon, @Field("cor") String color) {}
