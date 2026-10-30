# Revisão da PR #63 com astro-api-development

Este arquivo registra uma revisão e seus resultados no momento da PR. Para o
contrato atual, consulte [formulários](forms.md) e [respostas](forms-responses.md).

Escopo: endpoints de formulários do astro-api e suas integrações. Skill instalada em
`<CODEX_HOME>/skills/astro-api-development` a partir do ZIP fornecido.
Foram lidos o SKILL.md e `references/requisitos-trabalho.md`.

## Verificações

| Critério | Evidência no código e nos testes |
| --- | --- |
| Organização por domínio e camadas | Controllers recebem HTTP; FormResponseService coordena envio e análise; repositories acessam dados; conversões públicas ficam em FormMapper/FormResponseMapper/FormAnswerMapper. |
| Contrato e validação | Requests Jakarta usam mensagens em português por chaves; FormContractTest confere constraints e contrato camelCase. Sem var, JsonProperty ou JsonIgnoreProperties em forms. |
| Identidade e autorização | Firebase + Spring Security, PreAuthorize e AuthenticatedUser. FormAccessService verifica conta, unidade e workspace. Apenas destinatário responde; remetente/gestor do workspace analisa. |
| 401/403 por escopo | FormResponseRequestMatcher limita os novos retornos de autenticação às novas rotas; FormResponseSecurityTest também verifica a preservação das rotas antigas. |
| Dados e consultas | Mongo usa filtros por workspace/participante, paginação e gravações condicionais. A projeção de coordenadas usa UnitRepository com Query e parâmetros nomeados, conforme AuthRepository. A extração de POINT exige a query nativa; não foi inventada procedure. |
| Respostas e erros | ApiResult em respostas e erros; exceções conhecidas passam por GlobalExceptionHandler. Content-Type não suportado agora retorna 415 com envelope nas novas rotas. |
| Documentação | Operation e ApiResponse/ApiResponses cobrem as novas rotas; docs/forms.md e docs/forms-responses.md descrevem o contrato. |
| Responsabilidade dos tipos | Documento e valores embutidos persistidos em model; transporte interno em dto.internal; contexto em service; condições/filtros em repository. |
| Concorrência e falhas parciais | Mongo usa versão/estado/participante para impedir duplicação. Upload é compensado em falha conhecida; confirmação Mongo incerta tenta recuperar o envio e preserva imagens sem confirmação. Não existe transação distribuída entre Mongo, PostgreSQL, Redis e R2. |
| Localização | API retorna unitLocation, sem receber GPS; captureRequired é false. Verificação antes do início/a cada minuto e bloqueio sem coordenadas são responsabilidades do Android. |
| Estados | Pendentes vencidos podem responder; concluídos/reprovados constam da lista; viewed é por usuário. Reprovação não reabre o envio. |
| Privacidade | Não foram adicionados logs de tokens, senhas, corpo ou conteúdo de arquivo. Imagens usam chaves internas e URL assinada; credenciais ficam fora do Git. |

## Ajustes desta revisão

- Removidos os comentários remanescentes em código e testes de forms; explicações de recuperação e persistência ficam na documentação externa.
- Documentados no OpenAPI os retornos de sucesso e erros das novas rotas, incluindo 401, 403, 409, 413 e 415 quando aplicáveis.
- Corrigido o tratamento de Content-Type não suportado nas novas rotas: antes caía no erro genérico 500; agora retorna 415 com ApiResult. Os outros endpoints preservam o tratamento existente.

## Requisitos acadêmicos e limites

O domínio forms atende à parte NoSQL usando Spring MVC e Spring Data MongoDB. Reutiliza
PostgreSQL/JPA para referências de usuários e unidades, Spring Security integrado ao Firebase
e Swagger/OpenAPI. Esta revisão não declara o trabalho escolar inteiro aprovado, nem cria
novas APIs, bancos ou rotinas SQL fora do escopo solicitado.

A query de geolocalização foi validada com PostgreSQL local isolado; persistência e fluxo HTTP
com Mongo local isolado. ViewedService e R2 são simulados nos testes do módulo; a assinatura
R2 é validada localmente. Não houve upload real, alteração de banco de produção ou deploy.

Validação final: `mvn verify` com PostgreSQL e MongoDB locais isolados: 520 testes reportados, 488 aprovados, 32 ignorados e nenhuma falha/erro. Inclui o caso HTTP 415 com envelope, autenticação 401/403 e persistência de formulários..
