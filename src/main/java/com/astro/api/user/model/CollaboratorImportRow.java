package com.astro.api.user.model;

public record CollaboratorImportRow(int number, String name, String cpf, String email,
                                    String unit, String role, String workModel) { }
