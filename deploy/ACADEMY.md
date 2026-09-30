# CD da segunda API — SCRUM-393

Fluxo: PR de código aprovada → main → Maven verify → imagem GHCR com SHA →
branch no astro-gitops → PR de tag aprovada → main GitOps → Argo CD → EKS.

O CI existente permanece intacto. PRs neste workflow executam Maven e Docker,
mas não publicam nem propõem deploy. Também é possível Run workflow na main.
Nenhum segredo AWS ou da aplicação é necessário no GitHub Actions.

Antes do merge desta PR:

1. Aprovar a PR inicial do GitOps (inclui as duas APIs):
   https://github.com/Astro-Inter/astro-gitops/pull/1.
2. Configurar neste repo o Secret Actions ASTRO_GITOPS_TOKEN. Token fine-grained,
   owner Astro-Inter, SOMENTE o novo repo astro-gitops; Contents e Pull requests
   Read and write, expiração curta. Sem administração, workflows ou outros repos.
   Não compartilhar nem commitar seu valor. Aprovar o token na organização se exigido.
3. Fazer merge desta PR e aguardar validate, publish e update-gitops.
   Imagem: ghcr.io/astro-inter/astro-api:sha-<SHA completo>.
4. Aprovar e fazer merge da PR de tag aberta no GitOps. A automação nunca escreve
   diretamente na main, nunca aprova e nunca faz merge.
5. Seguir [o guia de ativação da API](https://github.com/Astro-Inter/astro-gitops/blob/main/docs/ASTRO-API.md).

O namespace é astro-api, sem compartilhar Secrets com astro-ai.
PostgreSQL, Redis e Firebase continuam externos ao cluster.
Inicialmente o Argo fica em sync manual para evitar tentar baixar uma imagem
ainda não publicada. Após imagem aprovada e Secrets prontos, habilitar auto-sync
em nova PR no GitOps para completar as atualizações automáticas.

Maven usa os testes existentes sem credenciais reais. O teste de contexto completo
já está desabilitado no projeto por depender de banco/Firebase. Testes verdes e
build Docker NÃO comprovam as integrações de produção: validar antes da ativação.
