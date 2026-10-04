package com.astro.api.user.repository;

import java.time.LocalDate;

public interface UserNrValidityProjection {
    Integer getNrId();

    LocalDate getValidity();
}
