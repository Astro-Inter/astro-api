# Geolocalização de unidades

A API consulta o [Geoapify Forward Geocoding](https://apidocs.geoapify.com/docs/geocoding/forward-geocoding/)
ao criar unidades no cadastro inicial (`POST /register-workspace`), nas configurações
(`POST /units`) e na importação que cria novas unidades. `PUT /units/{unitId}`
repete a busca, inclusive quando o endereço não mudou, permitindo recuperar uma
localização que não foi encontrada anteriormente.

O web continua enviando `UnitRequest`, sem chave Geoapify ou coordenadas. Não há
novo endpoint público: autenticação, permissões, escopo de workspace, payloads,
status HTTP e envelope `ApiResult` permanecem os mesmos.

## Configuração

Defina `GEOAPIFY_API_KEY` no ambiente da API ou no `.env` local, ignorado pelo Git.
A chave não deve estar no frontend, em arquivos versionados ou em logs. O exemplo
de configuração está em `.env.example`. Configure essa variável também no
ambiente de deploy para habilitar a busca em produção.

A consulta usa rua, número, bairro, cidade, UF e CEP, com filtro de país `br`,
idioma português e limite de um resultado. Nome da unidade e complemento não são
enviados. A conexão tem limite de dois segundos e a requisição, de quatro segundos
por unidade. Não há repetição automática na mesma operação.

## Persistência e falhas

O [schema externo](https://github.com/Astro-Inter/banco-de-dados/blob/main/database/scripts/1_create_table.sql)
precisa conter `unidade_endereco.geolocalizacao POINT`, já usado
pela consulta de localização de formulários. A API não cria nem altera esse schema.
Uma query nativa grava `point(longitude, latitude)` pelo ID do endereço já
persistido. Não é PostGIS e não exige adicionar dependências espaciais ao JPA.

Quando a busca retorna coordenadas válidas, elas são gravadas na mesma transação
PostgreSQL do endereço. A consulta externa é somente leitura e não participa da
transação do banco.

- Na criação, ausência de resultado, chave ausente, erro HTTP, limite de cota,
  resposta inválida ou falha de conexão não impedem salvar a unidade. A localização
  fica nula e o cliente recebe o sucesso habitual.
- Na edição, a busca é repetida. Se falhar e o endereço for o mesmo, as coordenadas
  existentes são preservadas. Se rua, número, bairro, cidade, UF ou CEP mudar, a
  localização antiga é apagada para não indicar outro endereço. Nome e complemento
  não alteram a localização por si só.
- Falhas de persistência não são ocultadas pelo tratamento de falhas do Geoapify:
  a operação continua sujeita a rollback e ao handler global da API.

Não há preenchimento retroativo automático de unidades existentes. A edição da
unidade é o caminho para tentar novamente. Sem coordenadas, os consumidores de
localização continuam seguindo seu tratamento existente para localização ausente.

Os testes cobrem o cliente HTTP sem chamadas externas, cadastro em lote,
configurações, repetição após falha e remoção de coordenadas antigas. O teste
`UnitRepositoryPostgresTest` valida a gravação e limpeza do `POINT` em PostgreSQL
descartável quando `UNIT_TEST_JDBC_URL` está configurada conforme a CI.
