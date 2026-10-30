# Push de chat — SCRUM-674

ChatService grava chat_push_outbox na mesma sessão/transação Mongo da mensagem.
O _id é o ID determinístico da mensagem; a repetição de clientMessageId retorna a
mensagem existente sem gerar outro evento. O envio HTTP ocorre fora dessa transação.
Uma falha externa não desfaz uma mensagem persistida. Falha ao gravar a outbox
impede a transação inteira: não se confirma uma mensagem sem seu evento durável.
Mongo deve oferecer transações, como já exigido pelo envio do chat.

ChatPushDispatcher usa um scheduler próprio e revalida participantes antes de
cada encaminhamento: status, unidade ativa, workspace, papéis e UID destinatário.
Revogação descarta o evento sem enviar sua prévia. Consulta indisponível provoca
nova tentativa. A revalidação ocorre na API, antes do aceite durável do Worker;
uma revogação posterior a esse aceite não cancela um push já encaminhado.

## Configuração

- CHAT_PUSH_ENABLED=false por padrão. Nenhuma mensagem antiga é recuperada.
- CHAT_PUSH_URL: URL HTTPS completa do Worker, incluindo /internal/chat-messages.
  Não aceita credenciais na URL, query, fragmento ou redirecionamento.
- CHAT_PUSH_SECRET: segredo aleatório compartilhado com o Worker, 32–256 caracteres,
  armazenado no gerenciador de secrets da infraestrutura; nunca no repositório.
- astro.chat.push.poll-ms: intervalo de consulta, padrão 1.000 ms.

Provisionar a coleção chat_push_outbox e permitir inserts/updates e índices:
composto done/nextAttempt e TTL expiresAt. O dispatcher também cria os índices
idempotentemente. Uma rodada reivindica até 10 eventos; lease atômico de 120s
impede disputa normal entre réplicas. Falhas/HTTP diferente de 200/202 usam espera
progressiva de 1s a 300s, até o evento expirar em 24h. Timeout HTTP de 10s,
conexão de 5s. Nenhum token Firebase é enviado por essa integração.
O cliente HTTP é um bean próprio injetado pelo único construtor público do
dispatcher, sem @Autowired. A inicialização tardia dos índices não precisa de
synchronized no fluxo atual: claim é chamado somente pelo dispatcher executado
no chatPushScheduler de uma thread por instância. A exclusão entre réplicas
permanece no findOneAndUpdate atômico e no lease Mongo. Falhas na criação dos
índices deixam a inicialização pendente para a rodada seguinte.

## Contrato interno versão 1

POST /internal/chat-messages, Content-Type application/json,
Authorization Bearer com CHAT_PUSH_SECRET. Corpo com exatamente:
version (número 1); messageId e conversationId (UUID); senderId, recipientId e
workspaceId (strings decimais para preservar precisão); recipientUid (Firebase);
senderName (até 80 pontos Unicode); preview (até 160); createdAt (ISO-8601).
O mapper normaliza espaços e limita a prévia preservando Unicode.
Internamente, ChatPushEvent usa IDs Long e ChatPushOutboxEntry representa o
evento reivindicado com sua contagem de tentativas. Somente o mapper/repository
conhecem BSON; elegibilidade e dispatcher recebem modelos tipados. O mapper
mantém IDs como strings e a data ISO-8601 na persistência e no JSON do Worker.

202 significa persistência durável no Worker, não entrega no dispositivo.
Após aceite, a outbox remove payload e lease. O TTL elimina o recibo em até 24h
mais o atraso normal do monitor TTL. O Worker guarda deduplicação por sete dias
e apaga a prévia após conclusão/expiração. Retentativas usam o mesmo messageId.
Não existe transação entre Mongo, HTTP, Durable Objects e FCM; timeout após
aceite pode repetir a chamada. O Worker e Android filtram duplicatas.

## Ativação e validação

Preparar primeiro Worker com ingress HTTPS, secrets Firebase e compartilhado;
ativar o Worker, validar autenticação e só então ativar a API. Para interromper
novos eventos, desabilitar CHAT_PUSH_ENABLED. Eventos já aceitos pelo Worker
precisam também do ENABLED=false no Worker; a prévia expira em 24h.
Não foi feito deploy nesta implementação.

Testes: ./mvnw.cmd '-Dtest=Chat*Test' test. Cobertura de persistência com a sessão
fornecida, chave determinística, lease/TTL, autenticação HTTP, descarte por acesso
revogado, falha/retry, prévia Unicode e integração do evento no fluxo de envio.
Testes de Mongo usam mocks; não comprovam transação real em réplica Mongo.
Quatro testes PostgreSQL condicionais exigem CHAT_TEST_JDBC_URL local apropriada.
Teste ponta a ponta deve confirmar mensagem, outbox, aceite Worker e aviso Android
com duas contas autorizadas, sem registrar corpos, tokens ou secrets.
