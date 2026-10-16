package com.astro.api.conformity.model;

import java.time.LocalDate;

public record ConformityImportRow(int row, String cpfText, String nrText, String cpf,
                                  Integer nrId, LocalDate expiresAt, String error) { }
