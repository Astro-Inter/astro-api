# ASTRO API

API REST responsável pelo backend do **ASTRO**, centralizando as regras de negócio e o acesso aos dados relacionados à gestão de conformidade com Normas Regulamentadoras (NRs).

## Sobre o ASTRO

O ASTRO é um sistema de gestão de conformidade com NRs, criado para reduzir o esforço manual do RH e da área de Segurança e Saúde do Trabalho (SST). Ele auxilia no acompanhamento das NRs aplicáveis, eventos, conformidades e demais processos relacionados à gestão de SST.

O sistema atende três interfaces:

- App mobile do Gestor
- App web do Gestor
- App mobile do Colaborador

Todas as interfaces consomem uma única API backend.

### Como o sistema funciona

- **Workspace:** representa a empresa e reúne unidades, cargos, pessoas e suas atividades. O gestor do workspace administra o ambiente e pode transferir sua responsabilidade.
- **Pessoas e acesso:** colaboradores possuem uma conta; gestores são colaboradores com permissões adicionais. O primeiro acesso envolve a verificação de uma chave e a ativação da conta. A autenticação posterior é realizada pelo Firebase.
- **Cargos, unidades e NRs:** o cargo determina as NRs individuais exigidas do colaborador. As NRs associadas à unidade fornecem contexto para operações e inspeções. Colaboradores podem ser cadastrados ou sincronizados por planilha.
- **Conformidades:** representam a situação de uma pessoa em relação às NRs, com registro de validade e acompanhamento por gestores. Há fluxos de registro manual, importação e conclusão de eventos.
- **Eventos:** treinamentos e outras atividades são organizados em turmas e participantes. Gestores acompanham conclusões e evidências; colaboradores consultam eventos e registram conclusões nos fluxos disponíveis.
- **Formulários:** gestores criam e publicam formulários, acompanham respostas e fazem análises. As respostas podem incluir imagens privadas.
- **Acompanhamento e comunicação:** as telas de Home reúnem indicadores, enquanto notificações e chat apoiam o acompanhamento e a comunicação entre pessoas do workspace.

O ASTRO apoia a organização e a tomada de decisão em SST; a responsabilidade legal continua com a empresa e os profissionais habilitados.

## Stack e arquitetura

A API usa **Java 21**, **Spring Boot 4.1**, Maven, Spring MVC, Spring Security, Spring Data JPA, MongoDB e Redis. O Firebase Authentication gerencia a identidade; Cloudflare R2 armazena arquivos; Geoapify fornece geocodificação; e WebSocket/STOMP e Redis Pub/Sub apoiam o chat. Springdoc fornece OpenAPI e Swagger, e OpenTelemetry permite exportar logs.

O código está organizado por domínio em `src/main/java/com/astro/api`. Cada domínio contém as camadas necessárias, como controller, service, repository, model, DTO e mapper:

| Pacotes | Responsabilidade |
| --- | --- |
| `auth`, `user`, `manager` | Autenticação, contas, colaboradores e gestores |
| `workspace`, `unit`, `role` | Workspaces, unidades e cargos |
| `conformity`, `nrreview` | Conformidades, catálogo e revisão de NRs |
| `event`, `form` | Eventos, conclusões, formulários e respostas |
| `home`, `notification`, `chat` | Indicadores, notificações e comunicação |
| `common`, `config` | Respostas, tratamento de erros e configurações compartilhadas |

A API acessa PostgreSQL, MongoDB e Redis. O schema PostgreSQL é mantido no [repositório de banco de dados](https://github.com/Astro-Inter/banco-de-dados); consulte os scripts e a documentação desse repositório para preparar uma instância compatível.

## Como executar localmente

### 1. Pré-requisitos

- JDK 21 instalado e disponível em `java -version`.
- PostgreSQL com o schema e os objetos necessários à API, MongoDB e Redis acessíveis. O repositório não inicia esses serviços automaticamente.
- Projeto Firebase e JSON de uma conta de serviço para o Firebase Admin. A aplicação inicializa o Firebase ao subir.
- Acesso à internet para o Maven Wrapper baixar o Maven e as dependências. Não é necessário instalar Maven separadamente.
- Para testar uploads, configure também os buckets e credenciais do R2; para geocodificação de endereços, configure o Geoapify.

### 2. Obtenha o código e prepare os serviços

```bash
git clone https://github.com/Astro-Inter/astro-api.git
cd astro-api
```

Prepare o PostgreSQL usando os scripts e as instruções do repositório de banco de dados, crie ou selecione um banco MongoDB e inicie o Redis. Use as mesmas conexões e prefixos Redis do worker de e-mail para testar o envio e a verificação das chaves de acesso.

### 3. Configure o ambiente

Copie `.env.example` para `.env` na raiz da API e preencha os valores do seu ambiente. No PowerShell:

```powershell
Copy-Item .env.example .env
```

O Spring carrega o `.env` como arquivo de propriedades na inicialização. As variáveis também podem ser fornecidas diretamente no ambiente. Não envie o `.env` nem o JSON de credenciais ao repositório.

Para gerar o Base64 do JSON do Firebase no PowerShell, substitua o caminho abaixo pelo arquivo da sua conta de serviço e copie o resultado para `FIREBASE_CREDENTIALS_BASE64` no `.env`:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\caminho\firebase-service-account.json'))
```

| Variável | Configuração |
| --- | --- |
| `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_NAME`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Endereço, banco e credenciais PostgreSQL. |
| `POSTGRES_MAX_POOL_SIZE` | Máximo de conexões por instância; padrão `3`. |
| `MONGODB_URI`, `MONGODB_DATABASE` | URI completa, por exemplo `mongodb://localhost:27017`, e nome do banco. |
| `REDIS_HOST`, `REDIS_PORT` | Endereço e porta do Redis. |
| `REDIS_USERNAME`, `REDIS_PASSWORD` | Credenciais do Redis, quando exigidas. |
| `WORKSPACE_ACCESS_TOKEN_PREFIX`, `ACCESS_TOKEN_PREFIX` | Prefixos das chaves de acesso no Redis; devem coincidir com os do worker de e-mail. |
| `FIREBASE_PROJECT_ID`, `FIREBASE_CREDENTIALS_BASE64` | ID do projeto Firebase e JSON da conta de serviço codificado em Base64. |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula. Padrão: `https://astro-inter.github.io`. |
| `GEOAPIFY_API_KEY` | Chave para geocodificar unidades; veja [geolocalização](docs/unit-geolocation.md). |
| `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | Endereço e credenciais S3 do Cloudflare R2 para uploads e leitura de arquivos. |
| `R2_BUCKET_AVATARS` ou `R2_BUCKET` | Bucket de fotos de perfil; `R2_BUCKET` tem prioridade quando definido. |
| `R2_BUCKET_EVIDENCIAS`, `R2_BUCKET_FORM_IMAGES` | Buckets para evidências de eventos e imagens de formulários. |
| `R2_PRESIGNED_URL_DURATION` | Validade das URLs assinadas; padrão `1d`. |
| `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_HEADERS` | Opcionais; configure ambos para exportar logs ao Grafana. |

### 4. Inicie e acesse a API

No Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

No Linux ou macOS:

```bash
./mvnw spring-boot:run
```

Por padrão, a aplicação fica disponível em `http://localhost:8080`.

## Testes

Execute a mesma verificação usada no CI:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

No Windows, substitua `./mvnw` por `.\mvnw.cmd`. Alguns testes de integração só são habilitados quando suas variáveis de ambiente apontam para serviços locais específicos; consulte as condições nos próprios testes antes de executá-los. O CI inicia MongoDB e PostgreSQL para parte desses testes. O teste de contexto completo está desabilitado por depender de banco e Firebase, portanto uma execução bem-sucedida não valida todas as integrações externas.

## Observabilidade

Os logs da aplicação aparecem no console. A exportação para o Grafana Cloud pode ser habilitada configurando as variáveis de OpenTelemetry descritas acima. Para detalhes de diagnóstico, consulte [docs/observability-diagnostics.md](docs/observability-diagnostics.md).
