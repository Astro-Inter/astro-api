# ASTRO API

API REST responsável pelo backend do **ASTRO**, centralizando as regras de negócio e o acesso aos dados relacionados à gestão de conformidade com Normas Regulamentadoras (NRs).

## Sobre o ASTRO

O ASTRO é um sistema inteligente de gestão de conformidade com NRs, criado para reduzir o esforço manual do RH e da área de Segurança e Saúde do Trabalho (SST). Ele auxilia no acompanhamento das NRs aplicáveis, eventos, conformidades e demais processos relacionados à gestão de SST.

O sistema atende três interfaces:

- App mobile do Gestor
- App web do Gestor
- App mobile do Funcionário

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

| Módulo | Responsabilidade |
|---|---|
| `workspace` | Gerenciamento do ambiente organizacional da empresa |
| `unidade` | Gerenciamento das unidades da empresa e seus endereços |
| `cargo` | Gerenciamento dos cargos da empresa |
| `usuario` | Gerenciamento dos usuários, incluindo Funcionários e Gestores |
| `evento` | Gerenciamento de Eventos, Turmas, participantes, conclusões e evidências |
| `conformidade` | Acompanhamento da situação individual dos usuários em relação às NRs aplicáveis |
| `formulario` | Gerenciamento de formulários |
| `chat` | Comunicação entre Gestores e Funcionários |
| `notificacao` | Gerenciamento de notificações e alertas |

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

| Variável | Descrição | Exemplo |
|---|---|---|
| `POSTGRES_HOST` | Host do PostgreSQL | `localhost` |
| `POSTGRES_PORT` | Porta do PostgreSQL | `5432` |
| `POSTGRES_USER` | Usuário do banco | `astro_user` |
| `POSTGRES_PASSWORD` | Senha do banco | `********` |
| `POSTGRES_NAME` | Nome do banco de dados | `astro` |
| `FIREBASE_PROJECT_ID` | ID do projeto no Firebase | `astro-app` |
| `FIREBASE_CREDENTIALS_BASE64` | Credenciais do Firebase codificadas em Base64 | `********` |

## Endpoints

*Em construção — a documentação completa dos endpoints fica disponível via Swagger conforme os módulos forem implementados.*

## Time

Consulte **`Responsabilidades Astro`** para a divisão completa das áreas e responsáveis pelo projeto.