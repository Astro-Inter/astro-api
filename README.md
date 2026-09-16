# ASTRO Core API


API REST responsável pela gestão de conformidade com Normas Regulamentadoras (NRs) do sistema **ASTRO** — empresas, unidades, cargos, funcionários, NRs, treinamentos, certificados e eventos.

## Sobre o ASTRO

O ASTRO é um sistema inteligente de gestão de conformidade com NRs, criado para reduzir o esforço manual do RH e da área de Segurança e Saúde do Trabalho (SST). Ele identifica quais NRs se aplicam a cada empresa, controla treinamentos obrigatórios, monitora validade de certificados e alerta antes de problemas com fiscalização.

O sistema atende três interfaces:
- App mobile do Gestor
- App web do Gestor
- App mobile do Funcionário
## Stack técnica

- **Java 17+**
- **Spring Boot** (Spring MVC)
- **Spring Data JPA**
- **PostgreSQL**
- **Spring Security** (JWT)
- **Swagger / OpenAPI** (springdoc-openapi)
- **Maven**
## Arquitetura

O projeto segue **Modular MVC**: em vez de organizar por camada técnica, cada módulo de domínio carrega seu próprio mini-MVC (model, repository, dto, service, controller). 
```
br.com.astro.core
│
├── common
│   ├── config          → SecurityConfig, SwaggerConfig
│   └── exception        → GlobalExceptionHandler, exceções customizadas
│
├── workspace            → Empresa, Workspace, Unidade
│   ├── model            → entidades JPA (Empresa, Workspace, Unidade)
│   ├── repository       → interfaces Spring Data JPA
│   ├── dto              → request/response
│   ├── service          → regras de negócio
│   └── controller       → endpoints REST
│
├── cargo                 → Cargo
│   ├── model
│   ├── repository
│   ├── dto
│   ├── service
│   └── controller
│
├── funcionario           → Funcionário
│   ├── model
│   ├── repository
│   ├── dto
│   ├── service
│   └── controller
│
├── nr                     → Norma Regulamentadora (NR)
│   ├── model
│   ├── repository
│   ├── dto
│   ├── service
│   └── controller
│
├── treinamento            → Curso, Certificado
│   ├── model
│   ├── repository
│   ├── dto
│   ├── service
│   └── controller
│
├── evento                 → Evento (calendário)
│   ├── model
│   ├── repository
│   ├── dto
│   ├── service
│   └── controller
│
└── conformidade           → status/pendências de conformidade
    ├── model
    ├── repository
    ├── dto
    ├── service
    └── controller
```

### Módulos de domínio

| Módulo | Entidades | Responsabilidade |
|---|---|---|
| `workspace` | Empresa, Workspace, Unidade | Cadastro da empresa, geração de chave de acesso, subdivisões, etc |
| `cargo` | Cargo | Cargos por unidade, sugestão automática de NRs aplicáveis, etc |
| `funcionario` | Funcionário | Cadastro (manual ou via planilha), vínculo com cargo, etc |
| `nr` | NormaRegulamentadora | Catálogo de NRs, configuração por cargo/workspace, etc |
| `treinamento` | Curso, Certificado | Treinamentos concluídos ou pendentes, etc |
| `evento` | Evento | Itens de calendário (treinamentos agendados, vencimentos), etc |
| `conformidade` | PerfilFuncionario | Liga funcionário às NRs que precisa cumprir e acompanha status/pendências, etc |

## Como rodar localmente

### Pré-requisitos
- JDK 17+
- Maven 3.9+
- PostgreSQL 15+ (local ou instância remota)
### Configuração

1. Clone o repositório
```bash
   git clone <url-do-repo>
   cd astro-core-api
```

2. Configure as variáveis de ambiente (veja [Variáveis de ambiente](#variáveis-de-ambiente))
3. Rode a aplicação
```bash
   ./mvnw spring-boot:run
```

4. A API sobe em `http://localhost:8080`
5. Documentação Swagger disponível em `http://localhost:8080/swagger-ui.html`
### Variáveis de ambiente

| Variável | Descrição | Exemplo |
|---|---|---|
| `DB_URL` | URL de conexão com o Postgres | `jdbc:postgresql://localhost:5432/astro` |
| `DB_USERNAME` | Usuário do banco | `astro_user` |
| `DB_PASSWORD` | Senha do banco | `********` |
| `JWT_SECRET` | Chave usada para assinar os tokens JWT | `********` |
| `JWT_EXPIRATION` | Tempo de expiração do token (ms) | `3600000` |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas (apps mobile/web) | `http://localhost:5173` |

## Endpoints

_Em construção — a documentação completa dos endpoints fica disponível via Swagger assim que os módulos forem implementados._

## Time

Ver `Responsabilidades Astro` para divisão completa de áreas e responsáveis do projeto.
 