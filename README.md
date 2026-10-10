# ASTRO API

API REST responsável pelo backend do **ASTRO**, centralizando as regras de negócio e o acesso aos dados relacionados à gestão de conformidade com Normas Regulamentadoras (NRs).

## Sobre o ASTRO

O ASTRO é um sistema inteligente de gestão de conformidade com NRs, criado para reduzir o esforço manual do RH e da área de Segurança e Saúde do Trabalho (SST). Ele auxilia no acompanhamento das NRs aplicáveis, eventos, conformidades e demais processos relacionados à gestão de SST.

O sistema atende três interfaces:

- App mobile do Gestor
- App web do Gestor
- App mobile do Colaborador

Todas as interfaces consomem uma única API backend.

## Stack técnica

- **Java 21**
- **Spring Boot**
- **Spring MVC**
- **Spring Data JPA**
- **Spring Data MongoDB**
- **Spring Data Redis**
- **PostgreSQL**
- **MongoDB**
- **Redis**
- **Spring Security**
- **Firebase Authentication**
- **Firebase Admin SDK**
- **Swagger / OpenAPI** (`springdoc-openapi`)
- **Maven**

## Arquitetura

O projeto segue **Modular MVC**: em vez de organizar toda a aplicação por camada técnica, cada módulo de domínio possui suas próprias camadas, como model, repository, DTO, mapper, service e controller.

A aplicação utiliza PostgreSQL, MongoDB e Redis dentro de uma única API.

```text
com.astro.api
│
├── config
│
├── common
│   ├── exception
│   ├── handler
│   └── response
│
├── auth
│   ├── security
│   └── service
│
├── workspace
│   ├── model
│   ├── repository
│   ├── dto
│   │   ├── request
│   │   └── response
│   ├── mapper
│   ├── service
│   └── controller
│
├── unidade
├── cargo
├── usuario
├── evento
├── conformidade
├── formulario
├── chat
└── notificacao
```

O módulo `workspace` representa o padrão estrutural utilizado pelos módulos de domínio. Cada domínio possui suas próprias camadas conforme suas necessidades:

```text
dominio
├── model          → modelos e entidades do domínio
├── repository     → acesso aos dados
├── dto
│   ├── request    → dados recebidos pela API
│   └── response   → dados retornados pela API
├── mapper         → conversão entre modelos e DTOs
├── service        → regras de negócio
└── controller     → endpoints REST
```

Nem todos os módulos precisam possuir todas essas pastas. A estrutura é criada conforme as responsabilidades de cada domínio forem implementadas.

### Módulos de domínio

| Módulo | Responsabilidade                                                                |
|---|---------------------------------------------------------------------------------|
| `workspace` | Gerenciamento do ambiente organizacional da empresa                             |
| `unidade` | Gerenciamento das unidades da empresa e seus endereços                          |
| `cargo` | Gerenciamento dos cargos da empresa                                             |
| `usuario` | Gerenciamento dos usuários, incluindo Colaboradores e Gestores                  |
| `evento` | Gerenciamento de Eventos, Turmas, participantes, conclusões e evidências        |
| `conformidade` | Acompanhamento da situação individual dos usuários em relação às NRs aplicáveis |
| `formulario` | Gerenciamento de formulários                                                    |
| `chat` | Comunicação entre Gestores e Colaboradores                                      |
| `notificacao` | Gerenciamento de notificações e alertas                                         |

## Como rodar localmente

### Pré-requisitos

- JDK 21
- Maven 3.9+
- PostgreSQL
- MongoDB
- Redis

### Configuração

1. Clone o repositório:

```bash
git clone <url-do-repo>
cd astro-api
```

2. Configure as variáveis de ambiente necessárias.

3. Rode a aplicação:

```bash
./mvnw spring-boot:run
```

4. A API sobe em:

```text
http://localhost:8080
```

5. Documentação Swagger disponível em:

```text
http://localhost:8080/swagger-ui.html
```

## Variáveis de ambiente

| Variável | Descrição                                     | Exemplo |
|---|-----------------------------------------------|---|
| `POSTGRES_HOST` | Host do PostgreSQL                            | `localhost` |
| `POSTGRES_PORT` | Porta do PostgreSQL                           | `5432` |
| `POSTGRES_USER` | Usuário do banco SQL                          | `astro_user` |
| `POSTGRES_PASSWORD` | Senha do banco SQL                            | `********` |
| `POSTGRES_NAME` | Nome do banco de dados SQL                    | `astro` |
| `POSTGRES_MAX_POOL_SIZE` | Máximo de conexões PostgreSQL por instância (padrão `3`) | `3` |
| `REDIS_HOST` | Host do Redis                                 | `localhost` |
| `REDIS_PORT` | Porta do Redis                                | `6379` |
| `REDIS_USERNAME` | Usuário do Redis                              | `default` |
| `REDIS_PASSWORD` | Senha do Redis                                | `********` |
| `FIREBASE_PROJECT_ID` | ID do projeto no Firebase                     | `astro-app` |
| `FIREBASE_CREDENTIALS_BASE64` | Credenciais do Firebase codificadas em Base64 | `********` |

## Observabilidade

A API envia logs da aplicação ao Grafana Cloud por OpenTelemetry (OTLP/HTTP) quando as duas variáveis abaixo estão preenchidas. Os logs continuam aparecendo no console. Sem elas, a exportação é desativada e a aplicação funciona somente com o logging local.

| Variável | Descrição |
|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | URL base do endpoint OTLP do Grafana Cloud, sem `/v1/logs` (por exemplo, `https://.../otlp`). |
| `OTEL_EXPORTER_OTLP_HEADERS` | Cabeçalho de autenticação no formato OTLP, por exemplo `Authorization=Basic%20<credencial-base64>`. |

Copie as chaves vazias de `.env.example` para o `.env` local e preencha somente no seu ambiente. O valor do cabeçalho deve ser codificado para URL; não coloque aspas nem espaços ao redor de `=`. A exportação acrescenta `/v1/logs` à URL base e identifica o serviço como `astro-api`.

Em produção, forneça as duas variáveis como secrets do serviço que executa o container. Os workflows deste repositório apenas testam e publicam a imagem; não executam a API e, por isso, não precisam receber as credenciais do Grafana. Se a API passar a rodar diretamente no GitHub Actions, crie os secrets `GRAFANA_OTLP_ENDPOINT` e `GRAFANA_OTLP_HEADERS` e mapeie-os para `OTEL_EXPORTER_OTLP_ENDPOINT` e `OTEL_EXPORTER_OTLP_HEADERS` no passo que a executa.

Para testar, inicie a API normalmente com o `.env` preenchido e produza um log de aplicação. No Grafana Cloud, abra **Explore → Logs** e filtre por `service_name="astro-api"` no período recente. Também é possível iniciar sem essas variáveis e verificar que os logs continuam no console. Não registre credenciais ou outros dados sensíveis em mensagens de log.

## Endpoints

### Deploy no Render

Crie um **Web Service** com runtime **Docker**, usando o `Dockerfile` da raiz.
O Render executa o `CMD` da imagem; não é necessário configurar um comando
de inicialização separado. A API escuta em `0.0.0.0` na porta `PORT` fornecida
pelo Render (padrão `10000`). Use a verificação TCP padrão se não configurar
um endpoint público de saúde; `/user/me` exige autenticação.

O `.env` local é excluído da imagem. Cadastre as variáveis em **Environment**
no painel do serviço, sem aspas envolvendo os valores:

| Serviço | Variáveis necessárias |
|---|---|
| PostgreSQL | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_NAME`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. |
| MongoDB | `MONGODB_URI` (URI completa iniciando com `mongodb://` ou `mongodb+srv://`) e `MONGODB_DATABASE`. |
| Redis | `REDIS_HOST`, `REDIS_PORT`; `REDIS_USERNAME` e `REDIS_PASSWORD` conforme a autenticação do provedor. |
| Firebase | `FIREBASE_PROJECT_ID` e `FIREBASE_CREDENTIALS_BASE64` (JSON da conta de serviço codificado em Base64). |
| Cloudflare R2 | `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_AVATARS` (ou `R2_BUCKET`). A credencial deve permitir leitura, escrita e exclusão no bucket de avatares. |

`POSTGRES_MAX_POOL_SIZE` é opcional e usa `3` por instância.
`R2_PRESIGNED_URL_DURATION` é opcional e usa `15m`.
`R2_BUCKET_EVIDENCIAS` permanece no exemplo para outros fluxos; este endpoint
usa exclusivamente o bucket de avatares.
`OTEL_EXPORTER_OTLP_ENDPOINT` e `OTEL_EXPORTER_OTLP_HEADERS` são opcionais;
preencha ambos apenas se for habilitar a exportação de logs ao Grafana.

Autorize os endereços de saída do serviço Render nas regras de acesso dos bancos
e nas restrições de IP da credencial R2, se aplicáveis. No MongoDB Atlas,
configure os endereços em **Network Access**.

A imagem usa Java 21 com heap máximo de 60% da memória disponível, reservando
o restante para memória nativa, threads e metadados. `JAVA_TOOL_OPTIONS` pode
ser sobrescrita no Render para ajustar esse valor. Isso não garante capacidade
sob carga: monitore a memória total e a latência, especialmente durante uploads
simultâneos de fotos de até 5 MB. As imagens persistem no R2 e as referências,
no PostgreSQL.

Referências: [Docker no Render](https://render.com/docs/docker),
[verificações de saúde](https://render.com/docs/health-checks) e
[integração MongoDB Atlas/Render](https://www.mongodb.com/docs/atlas/reference/partner-integrations/render/).

### Foto de perfil no Cloudflare R2

`PUT /user/me/profile-photo` recebe `multipart/form-data`, com a imagem no campo
`file` (JPEG, PNG ou WebP de até 5 MB). A API identifica o formato pelo cabeçalho
do arquivo, gera a chave `usuarios/<usuario_id>/<uuid>.<extensão>`, envia ao R2 e
salva `usuario_id` e `caminho_objeto` na tabela `usuario_foto_perfil`. Uma nova foto
substitui a referência na mesma linha (`usuario_id` é a chave primária), e o
objeto anterior é excluído do R2 após salvar a nova referência. O endpoint retorna
`204` após concluir o upload, a gravação e a exclusão da foto anterior.
Se o banco falhar, a API tenta remover o objeto recém-enviado e preserva a foto
anterior. R2 e PostgreSQL não compartilham uma transação: uma falha na exclusão
da foto anterior retorna erro, mas a nova referência já está salva; o objeto
anterior pode exigir limpeza posterior.

Exemplo de envio pelo mobile ou outro cliente HTTP:

```bash
curl -X PUT 'http://localhost:8080/user/me/profile-photo' \
  -H 'Authorization: Bearer <token-firebase>' \
  -F 'file=@foto.jpg'
```

`GET /user/me` retorna `profilePhotoUrl`,
uma URL assinada para leitura desse objeto. O mobile
deve usar `profilePhotoUrl` como fonte da imagem e consultar `/user/me` novamente
quando a URL expirar. Sem foto cadastrada, `profilePhotoUrl` é `null`.

Configure as variáveis abaixo no ambiente da API (ou no `.env` local):

| Variável | Descrição |
|---|---|
| `R2_ENDPOINT` | Endpoint S3 do R2, por exemplo `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`. |
| `R2_BUCKET` | Nome do bucket que contém as fotos. Se ausente, usa `R2_BUCKET_AVATARS`. |
| `R2_ACCESS_KEY_ID` | Access Key ID de uma credencial S3 do R2 com permissões de leitura, escrita e exclusão no bucket. |
| `R2_SECRET_ACCESS_KEY` | Secret Access Key correspondente, fornecida como secret. |
| `R2_PRESIGNED_URL_DURATION` | Validade da URL, padrão `15m`; aceita de `1s` a `7d`. |

A assinatura é gerada na API sem baixar a imagem nem verificar a existência do
objeto. O mobile faz o download diretamente do R2; uma chave inexistente resulta
em erro ao carregar a imagem. O bucket pode permanecer privado. As credenciais
ficam na API, e a URL permite leitura a quem a possuir até expirar. A integração
é inicializada ao solicitar uma foto; perfis sem foto não exigem configuração R2.
Com foto cadastrada e configuração ausente ou inválida, a consulta falha.

Referência: [URLs assinadas no Cloudflare R2](https://developers.cloudflare.com/r2/api/s3/presigned-urls/).

### Logs das requisições HTTP

Cada chamada aos endpoints síncronos gera um registro ao terminar, inclusive quando a autenticação ou validação rejeita a chamada. O registro contém método, rota (o template do endpoint quando disponível), status HTTP, duração em milissegundos e um identificador gerado pela API, também retornado no cabeçalho `X-Request-ID`. Respostas 2xx/3xx usam INFO, 4xx usam WARN e 5xx usam ERROR. Corpos, cabeçalhos de autenticação e query strings não são incluídos nesse registro.

No Grafana, selecione a fonte de logs e consulte no Explore:

```logql
{service_name="astro-api"} |= "HTTP request"
```

Para uma rota específica, acrescente `| http_route="/verify-email"`; para erros, use `| http_status >= 400`. Os campos `http_method`, `http_route`, `http_status`, `duration_ms`, `request_id` e `event` são enviados como atributos do log. O envio ocorre em lotes, portanto aguarde alguns segundos e atualize o intervalo recente. Esses registros acompanham as chamadas HTTP; métricas e traces distribuídos continuam desativados.

*Em construção — a documentação completa dos endpoints fica disponível via Swagger conforme os módulos forem implementados.*

## Time

Consulte **`Responsabilidades Astro`** para a divisão completa das áreas e responsáveis pelo projeto.
