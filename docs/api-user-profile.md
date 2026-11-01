# Perfil da pessoa autenticada

As rotas exigem `Authorization: Bearer <Firebase ID token>` e usam o UID da
identidade autenticada para localizar a própria conta.

## Consultar perfil

`GET /user/me` retorna `200` no envelope `ApiResult`. O objeto `data` contém
`nome`, `cargo`, `unidade`, `modalidade`, `email`, `profilePhotoUrl` e `nrs`.
Cada NR contém `code`, `validade`, `descricao`, `objetivo` e `aplicabilidade`.
As NRs são obtidas a partir do cargo e dos dados de validade do usuário, com
detalhes do catálogo MongoDB. Para cada NR do cargo, a validade vem da
conformidade de maior ID daquele usuário e NR, inclusive se não aplicável; pode
ser nula. Documentos ausentes ou revogados no MongoDB são omitidos. Sem cargo
ou NRs disponíveis, a lista é vazia.
A URL de foto é temporária e pode ser nula. Usuário inexistente retorna `404`.

## Atualizar foto

`PUT /user/me/profile-photo` recebe `multipart/form-data`, com arquivo na parte
`file`. Aceita JPEG, PNG ou WebP de até 5 MB. Em sucesso retorna `204 No
Content`. A API envia a imagem ao R2, vincula o caminho ao usuário e invalida o
cache de avatar; a foto anterior é removida após a persistência da nova.

Arquivo inválido ou acima do limite retorna `400`; o limite da requisição pode
provocar `413`. Usuário inexistente retorna `404`. O armazenamento R2 e o banco
não compartilham transação; uma falha em uma etapa externa pode exigir
investigação. Para os dados de conta editáveis, veja [conta](api-account.md).

## Verificação

Os testes existentes incluem `UserControllerTest`, `UserProfilePhotoServiceTest`
e testes de armazenamento R2. Os testes simulados não verificam o bucket real.
