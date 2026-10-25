# SCRUM-619 — Administração das NRs

Todas as rotas em `/admin/nrs` exigem token Firebase válido e perfil ADMIN consultado no banco. Gestores e colaboradores recebem 403; ausência de autenticação recebe 401. O WEB da SCRUM-618 usa essas rotas, mantendo URLs próprias em inglês para suas páginas.

## Contrato HTTP

As respostas usam o envelope `ApiResult`. Snapshots contêm `document`, `revision` e `approvedRevision`. O documento preserva os campos MongoDB em português. A revisão é SHA-256 do BSON com chaves ordenadas; `approvedRevision` vale `absent` quando não há versão publicada. Em consultas de catálogo, esse último campo não é usado e também vale `absent`.

| Método | Caminho | Operação |
| --- | --- | --- |
| GET | `/admin/nrs/pendentes` | Listar candidatos por número crescente |
| GET | `/admin/nrs/pendentes/{id}` | Consultar candidato e revisões |
| GET | `/admin/nrs/catalogo` | Listar versões publicadas |
| GET | `/admin/nrs/catalogo/{id}` | Consultar versão publicada |
| PUT | `/admin/nrs/catalogo/{id}` | Editar com `{revision, content}` |
| POST | `/admin/nrs/pendentes/{id}/aprovar` | Aprovar com `{revision, approvedRevision, corrections?}` |

`content` e `corrections` aceitam apenas nome, descrição, objetivo, aplicabilidade, usabilidade, revogação e meses de reciclagem. Nome é obrigatório e tem limite de 300 caracteres; textos longos são obrigatórios, até 20.000 caracteres. Usabilidade aceita exatamente Colaborador ou Empresa. Meses são inteiros positivos e revogação é obrigatória. ID, criação e data da fonte são obtidos do documento armazenado. A data da fonte deve ser válida em DD/MM/AAAA antes da publicação.

Entradas inválidas retornam 400; consultas ausentes retornam 404; revisões alteradas e aprovação repetida retornam 409. A edição do catálogo preserva o candidato pendente e não cria sincronização SQL própria nesta entrega.

## Publicação e concorrência

A aprovação compara as revisões do candidato e do catálogo dentro de uma transação MongoDB com leitura SNAPSHOT e gravação MAJORITY. Publica a análise com as correções opcionais, remove o candidato e insere um job em `nr_catalog_sql_sync` na mesma transação. Não há publicação parcial em falhas MongoDB.

As coleções padrão são `nrs` e `nrs_pendentes`. Podem ser configuradas por `astro.mongodb.nr-collection` e `astro.mongodb.nr-pending-collection`; devem ser distintas e estar no mesmo banco. MongoDB precisa de replica set ou cluster com suporte a transações. Não há fallback para escritas parciais em standalone.

## Sincronização SQL

Após o commit MongoDB, o backend executa `CALL ler_json_nr(CAST(? AS text))` usando parâmetro preparado com um array JSON da NR aprovada: `id`, `nome`, `revogada`, `tempo_reciclagem_mes`. A procedure oficial faz upsert somente dessa NR. Deve existir no banco, com permissão de execução e chave única em `nr_catalogo.codigo_nr`.

O retorno acrescenta `sqlSynchronized`. Em falha SQL, a aprovação permanece concluída, o job persiste e esse campo vale false. O agendador repete a cada 30 segundos, configurável por `astro.nr-review.sql-sync-delay-ms`. O job só é removido após commit SQL. O WEB explica que não é necessário aprovar outra vez.

Não existe transação distribuída entre MongoDB e PostgreSQL. Cada tentativa usa bloqueio `pg_advisory_xact_lock(619, nrId)` e lê a versão publicada atual após obter o bloqueio. A transação SQL tem limite de 10 segundos; repetições são idempotentes e removem somente o próprio job. Leituras, conflitos e rollback MongoDB não chamam a procedure.

O sincronizador diário existente continua independente. Não existe endpoint para disparar a procedure separadamente.

## Validação

`NrReviewSecurityTest` cobre todos os endpoints com ADMIN, outros perfis, ausência e token inválido, além de payloads inválidos. `NrReviewMongoTest` cobre publicação, correção, conflitos, repetição, validação e rollback. `NrSqlCatalogIntegrationTest` usa a procedure oficial em schema e banco descartáveis para testar correções, falha SQL, repetição, preservação de outras NRs e leitura da versão mais recente.

Os testes de escrita exigem explicitamente bancos locais isolados:

```powershell
$env:NR_TEST_MONGODB_URI='mongodb://127.0.0.1:27019/?replicaSet=astroLocal'
$env:NR_TEST_POSTGRES_URL='jdbc:postgresql://127.0.0.1:5439/astro_nr_local'
mvn "-Dtest=NrReviewMongoTest,NrReviewSecurityTest,NrSqlCatalogIntegrationTest" test
```

Os bancos de teste precisam estar iniciados. PostgreSQL usa a identidade local de teste declarada na fixture; os schemas têm prefixo `nr_sql_test_` e são removidos ao terminar. O teste MongoDB remove apenas seu banco descartável com prefixo validado. O teste de rollback requer failpoint habilitado no mongod isolado (`enableTestCommands=1`); esse parâmetro não deve ser usado em bancos compartilhados. Sem essas variáveis, os testes de integração locais ficam desabilitados; os testes de segurança continuam executando.

As configurações locais de execução e os arquivos `.env` não fazem parte desta alteração. Nenhuma migration ou procedure é instalada automaticamente no ambiente compartilhado.
