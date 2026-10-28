# Solicitação de conclusão de evento

`POST /events/{eventId}/completion` permite que o participante ativo dos tipos `COLABORADOR`, `GESTOR` ou `GESTOR_WORKSPACE` envie uma solicitação de conclusão para um evento configurado no modo `COLABORADOR`.

Envie `multipart/form-data`. A parte `files` é opcional e pode ser repetida para encaminhar até três evidências. Cada arquivo aceita qualquer tipo MIME e tem limite de 50 MB; a requisição inteira aceita até 151 MB. Quando o evento exige evidência, é necessário enviar pelo menos um arquivo.

O envio só é aceito enquanto a turma do colaborador estiver em andamento. A resposta é `201` e registra a conclusão como `PENDENTE`, para validação futura pelo gestor criador do evento.

Se uma conclusão tiver sido rejeitada, um novo POST substitui todas as evidências anteriores e a coloca novamente como `PENDENTE`. Conclusões pendentes ou já concluídas não podem ser reenviadas.

`GET /events/collaborator` inclui `participationStatus` para mostrar a situação da conclusão de cada colaborador: `PENDENTE` quando ainda não foi enviada, `EM_ANALISE` após o envio e enquanto aguarda validação, `CONCLUIDO` após aprovação e `REJEITADO` quando o gestor solicita correção. A situação da conclusão é independente do prazo da turma.

`GET /events/collaborator/{eventId}` retorna os detalhes apenas para um participante autorizado e não altera estado. Para registrar a visualização, o cliente chama `PATCH /events/collaborator/{eventId}/viewed`; assim, `viewed` na listagem passa a `true` e a Home deixa de exibir o evento como novo. Essa operação não cria nem envia uma conclusão: `participationStatus` permanece `PENDENTE` até o colaborador enviar a conclusão para validação.

`PATCH /events/{eventId}/participants/{participantId}/completion/rejection`
permite que o gestor criador rejeite uma conclusão pendente de evento no modo
`COLABORADOR`, inclusive depois do término da turma. O corpo JSON exige
`rejectionReason` não vazio. A operação grava status `REJEITADO`, a data de
validação e a justificativa, e retorna `200` com `EventCompletionResponse` no
envelope `ApiResult`. Uma solicitação não pendente não pode ser rejeitada.

Exemplo:


```bash
curl -X POST "https://api.exemplo/events/42/completion" \
  -H "Authorization: Bearer <token>" \
  -F "files=@certificado.pdf" \
  -F "files=@foto.jpg"
```
