# Keycloak local reproduzivel

Esta pasta guarda a configuracao declarativa do realm usado pelos modulos OIDC
e SAML. O objetivo e permitir recriar o laboratorio sem repetir configuracoes
manuais no Admin Console.

## Arquivos

- `autenticacao-lab-realm.json`: realm, roles, clients OIDC e SAML, mappers e
  usuario de estudo;
- `start-keycloak.sh`: carrega o certificado publico SAML e inicia o Keycloak
  com `--import-realm`;
- `compose.yaml`: monta os arquivos e injeta variaveis sem versionar secrets.

O JSON contem placeholders. `OAUTH2_CLIENT_SECRET` e a senha do usuario de
estudo chegam pelo ambiente. O certificado SAML e extraido do PEM montado em
tempo de inicializacao. A chave privada nunca entra no container do Keycloak e
permanece em `.local/saml`, ignorada pelo Git.

## Primeira inicializacao

1. Crie `.env` a partir dos nomes documentados em `.env.exemple`.
2. Gere a chave e o certificado do Service Provider:

```bash
./scripts/generate-saml-credentials.sh
```

3. Inicie o Keycloak:

```bash
docker compose up -d keycloak
```

O realm importado cria:

- realm `autenticacao-lab`;
- realm roles `USER` e `ADMIN`;
- client confidencial `autenticacao-lab-client` com Authorization Code e PKCE
  `S256`;
- client `autenticacao-lab-saml` com respostas assinadas, validacao das
  assinaturas do SP, mappers de identidade e atributo `Role`;
- usuario configurado pelas variaveis `KEYCLOAK_TEST_*`, inicialmente com
  `USER`.

## Realm existente

O Keycloak ignora a importacao de inicializacao quando o realm ja existe. Isso
protege o estado mantido no volume `keycloak-data`. Portanto, alterar o JSON nao
sobrescreve automaticamente um ambiente existente.

Para um ambiente descartavel, remova explicitamente o volume e inicie novamente
somente quando a perda dos dados locais for intencional. Em um ambiente que
precisa preservar dados, aplique uma migracao administrativa controlada em vez
de recriar o realm.

O import de inicializacao e adequado ao laboratorio. Ele nao substitui backup,
restore, versionamento de migracoes nem uma instalacao de producao do Keycloak.

## Referencias oficiais

- Importacao e exportacao de realms:
  <https://www.keycloak.org/server/importExport>
- Execucao e importacao em containers:
  <https://www.keycloak.org/server/containers>
