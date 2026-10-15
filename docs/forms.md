# Formulários da web

As rotas usam `Authorization: Bearer <Firebase ID token>` e o envelope `ApiResult`.
Somente gestores ativos, com unidade ativa e workspace válido, têm acesso.
O workspace e o remetente são determinados pela identidade validada.

## Listagem

`GET /forms?search=clima&status=PENDENTE&page=0&size=20`

`search` procura texto literal no título e na descrição, sem diferenciar maiúsculas.
`status` aceita `RASCUNHO`, `PENDENTE` ou `CONCLUIDO`. Ambos são opcionais.
A página começa em zero, `size` aceita 1 a 100 e a busca até 240 caracteres.
A ordenação é por criação decrescente e, em caso de empate, identificador decrescente.

`data` contém `items`, `page`, `size`, `totalElements` e `totalPages` (zero sem resultados).
Cada item contém `id`, `titulo`, `descricao`, `quantidade_perguntas` e `status`.
Não são carregadas respostas na listagem.

O gestor do workspace consulta todos os formulários do seu workspace. Outros gestores
consultam os que enviaram e os publicados dos quais são destinatários. Rascunhos de
outros remetentes ficam ocultos ao destinatário.

## Persistência e compatibilidade

A collection é `formularios`, com um documento por formulário e perguntas embutidas.
O campo `id_workspace` é obrigatório para acesso pelas rotas web. Documentos antigos
sem esse vínculo não ficam acessíveis até uma migração explícita e conferida.
A Home mobile passa a consultar somente documentos com `status: PENDENTE`.
Documentos antigos precisam ter seu status definido antes de aparecer nessa consulta.
Não inferimos conclusão a partir de perguntas opcionais sem resposta.
