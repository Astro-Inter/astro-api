# Solicitação de conclusão de evento

`POST /events/{eventId}/completion` permite que o colaborador participante envie uma solicitação de conclusão para um evento configurado no modo `COLABORADOR`.

Envie `multipart/form-data`. A parte `files` é opcional e pode ser repetida para encaminhar até três evidências. Cada arquivo aceita qualquer tipo MIME e tem limite de 50 MB; a requisição inteira aceita até 151 MB. Quando o evento exige evidência, é necessário enviar pelo menos um arquivo.

O envio só é aceito enquanto a turma do colaborador estiver em andamento. A resposta é `201` e registra a conclusão como `PENDENTE`, para validação futura pelo gestor criador do evento.

Se uma conclusão tiver sido rejeitada, um novo POST substitui todas as evidências anteriores e a coloca novamente como `PENDENTE`. Conclusões pendentes ou já concluídas não podem ser reenviadas.

Exemplo:

```bash
curl -X POST "https://api.exemplo/events/42/completion" \
  -H "Authorization: Bearer <token>" \
  -F "files=@certificado.pdf" \
  -F "files=@foto.jpg"
```
