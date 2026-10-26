# Validação local de colaboradores e conformidades

O WEB local pode usar `VITE_API_URL=http://localhost:8085` com Vite na porta
5175. O proxy `/api` existente encaminha as chamadas autenticadas à API local.

Neste Windows, o seletor NIO do JDK falhou ao conectar seu socket Unix interno.
As opções abaixo usam Tomcat NIO2 e o transporte HTTP/1 oficial do Firebase,
preservando autenticação, TLS e autorização. São opcionais e desativadas por
padrão; não alteram os transportes da execução publicada.

```powershell
$env:LOG_PATH = Join-Path (Get-Location) 'target'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8085 --astro.firebase.http1-transport=true --astro.local.nio2=true'
```

O banco configurado no `.env` é compartilhado. Em 26/10/2026, após autorização,
foram aplicados em uma transação os SQLs do repositório
`banco-de-dados`: `01_dim_nr_catalogo.sql`,
`02_dim_resumo_funcionario.sql`, `03_vw_cargos.sql`, `05_vw_colaboradores.sql` e
`07_vw_conformidades.sql`. As definições anteriores e os comentários foram
salvos em `target/employee-profiles-rollback.sql`. A aplicação e a conferência
das cinco definições foram registradas em `target/employee-profiles-applied.json`.
Nenhuma tabela ou registro de usuário ou conformidade foi alterado.

A aba de colaboradores usa `GET /collaborators?excludeSelf=true`; os seletores
de conformidades continuam usando a consulta sem o parâmetro. A exclusão usa o
ID carregado da identidade autenticada e ocorre antes da consulta de fotos.

As consultas nativas de conformidades já aceitam os três perfis. Na validação
SQL com tabelas temporárias e rollback, foram conferidos os três perfis, todos
os status, listagem, detalhes e registros, workspace e unidades, aplicabilidade,
validade nula, empate pelo maior ID e contagens por cargo e unidade. Nenhuma view
ou dado compartilhado foi alterado por essa validação.

Na sessão autenticada do WEB local, a listagem retornou 142 usuários com
`excludeSelf=true`, e o seletor de conformidades retornou os 143 do workspace.
A diferença foi exatamente a própria conta ativa; todos os outros usuários
foram preservados. Os detalhes de uma gestora abriram normalmente. A listagem
de conformidades retornou 232 registros. Nesse workspace, há oito gestores e
um gestor do workspace, ainda sem conformidades aplicáveis registradas; não
foram criadas conformidades para testar a interface no banco compartilhado.

Verificações automatizadas: API com 565 testes (53 pulados, nenhuma falha),
WEB com 403 testes e build TypeScript/Vite aprovados, documentação com 153
testes aprovados e analyzer sem avisos. O lint do WEB contém avisos anteriores
em arquivos não alterados nesta demanda.

## Permissões por perfil

A gestão de colaboradores retorna `canManage` por registro. O gestor do
workspace administra todos os perfis autorizados. O gestor comum consulta os
três, mas não pode editar nem alterar o status de outros gestores ou do gestor
do workspace. A API faz a verificação antes de gravar ou acionar o Firebase;
fora do escopo o retorno continua 404, e falta de permissão sobre alvo visível
retorna 403. A própria conta continua oculta apenas na listagem que solicitar
`excludeSelf=true`.

Verificação desta extensão: 572 testes da API (53 pulados, nenhuma falha), 406
testes do WEB e build aprovados. Os testes cobrem bloqueio sem mutação dos alvos,
permissões do responsável, preservação de tipo/UID, limites de escopo, flags da
listagem e renderização dos menus. Na sessão local do gestor do workspace, a
API retornou `canManage=true` e o menu de uma gestora exibiu consulta, edição e
desativação. Nenhuma alteração em cadastro real foi salva durante essa inspeção.
