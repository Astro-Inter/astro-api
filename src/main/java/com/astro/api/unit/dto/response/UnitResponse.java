package com.astro.api.unit.dto.response;

public record UnitResponse(
        Long id_unidade,
        Long id_workspace,
        String nome,
        String status,
        long quantidade_colaboradores,
        String cep,
        String rua,
        String cidade,
        String bairro,
        String estado,
        String complemento,
        String numero
) {
    public UnitResponse(Long id_unidade, Long id_workspace, String nome, String status, long quantidade_colaboradores,
                        String cep, String rua, String cidade, String bairro,
                        String estado, String complemento) {
        this(id_unidade, id_workspace, nome, status, quantidade_colaboradores, cep, rua, cidade,
                bairro, estado, complemento, null);
    }
}
