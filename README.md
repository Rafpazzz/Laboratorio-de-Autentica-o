# Laboratorio de autenticacao com Spring Security

Este projeto e um laboratorio backend para estudar, implementar e comparar
diferentes formas de autenticacao e autorizacao no mesmo sistema.

O objetivo nao e apenas disponibilizar endpoints de login. Cada modulo mostra
como o Spring Security processa credenciais, cria uma identidade autenticada,
persiste ou valida essa identidade, aplica roles, protege contra CSRF e CORS e
encerra o estado de autenticacao.

O laboratorio implementa quatro modalidades:

1. login tradicional com sessao HTTP;
2. access token JWT e refresh token opaco;
3. OAuth 2.0 com OpenID Connect;
4. SAML 2.0.

OAuth 2.0/OIDC e SAML usam Keycloak como provedor de identidade. Sessao e JWT
usam os usuarios locais armazenados no PostgreSQL.

## Estado do projeto

O backend possui:

- cadastro e CRUD de usuarios locais;
- roles `USER` e `ADMIN`;
- senhas locais protegidas com BCrypt;
- migrations versionadas com Flyway;
- respostas de seguranca usando `ProblemDetail`;
- CORS e CSRF configurados por modalidade;
- contextos de autenticacao isolados;
- chain de fallback que nega rotas sem politica explicita;
- testes unitarios e testes das `SecurityFilterChain`.

Os ajustes destinados exclusivamente a producao estao registrados em
[`docs/ATUALIZACOES_FUTURAS.md`](docs/ATUALIZACOES_FUTURAS.md).

## Stack

| Tecnologia | Uso no projeto |
| --- | --- |
| Java 21 | linguagem e runtime |
| Spring Boot 4.1.0 | configuracao e ciclo de vida da aplicacao |
| Spring MVC | API HTTP e controllers REST |
| Spring Security | autenticacao, autorizacao, filtros, CSRF e sessoes |
| Spring Data JPA / Hibernate | persistencia de usuarios e refresh tokens |
| PostgreSQL | banco de dados relacional |
| Flyway | versionamento e validacao do schema |
| Keycloak 26.7.4 | OpenID Provider e Identity Provider SAML |
| Docker Compose | execucao local e importacao do realm do Keycloak |
| Maven Wrapper | build e execucao reproduzivel do Maven |
| OpenSSL | geracao das credenciais do Service Provider SAML |

## Principais dependencias

As dependencias diretas estao no [`pom.xml`](pom.xml):

- `spring-boot-starter-webmvc`: API REST com Spring MVC;
- `spring-boot-starter-security`: infraestrutura central do Spring Security;
- `spring-boot-starter-security-oauth2-client`: Authorization Code, OIDC e
  logout iniciado pelo client;
- `spring-boot-starter-security-oauth2-resource-server`: validacao do access
  token JWT;
- `spring-boot-starter-security-saml2`: Service Provider SAML e integracao com
  OpenSAML;
- `spring-boot-starter-data-jpa`: repositories e mapeamento ORM;
- `spring-boot-starter-flyway` e `flyway-database-postgresql`: migrations;
- `spring-boot-starter-validation`: validacao dos contratos HTTP;
- `postgresql`: driver JDBC usado em runtime.

Para testes, o projeto usa os starters de teste do Spring MVC, Security, OAuth2,
Resource Server, SAML, JPA, Flyway e Validation. JUnit, AssertJ, Mockito e
MockMvc sao disponibilizados por esses starters.

Algumas implementacoes importantes chegam de forma transitiva pelas
dependencias do Spring:

- Nimbus JOSE + JWT para assinatura e validacao JWT;
- OpenSAML para mensagens, metadata e assinaturas SAML;
- Jackson para serializacao JSON e `ProblemDetail`;
- Hibernate Validator para Bean Validation.

## Modalidades de autenticacao

### Sessao HTTP

O backend valida email e senha com `AuthenticationManager`, cria um
`SecurityContext` e o salva na `HttpSession`. O navegador usa o cookie opaco
`JSESSIONID`; credenciais e dados do usuario nao ficam dentro do cookie.

Principais caracteristicas:

- usuarios locais no PostgreSQL;
- protecao contra session fixation;
- timeout ocioso de duas horas por padrao;
- CSRF baseado em cookie;
- `/usuarios/**` autorizado somente para `ROLE_ADMIN`;
- logout remove apenas o contexto da modalidade de sessao.

Documentacao detalhada:
[`Authentication/session/README.md`](src/main/java/com/rafael/autenticacao/Authentication/session/README.md).

### JWT

O login devolve um access token JWT assinado com RS256. Cada chamada protegida
envia esse token como `Bearer`, e o Resource Server valida assinatura, issuer,
audience e expiracao sem consultar uma sessao HTTP.

O access token dura 15 minutos. O refresh token e opaco, enviado em cookie e
armazenado no banco apenas como hash. Ele possui rotacao de uso unico, deteccao
de reutilizacao, revogacao por familia e duracao absoluta de duas horas por
padrao.

Documentacao detalhada:
[`Authentication/jwt/README.md`](src/main/java/com/rafael/autenticacao/Authentication/jwt/README.md).

### OAuth 2.0 e OpenID Connect

O Spring atua como OAuth2 Client e OIDC Relying Party. O Keycloak autentica o
usuario, e o backend usa Authorization Code com PKCE para receber e validar a
identidade.

Depois do callback, a aplicacao cria um contexto OAuth2 local na `HttpSession`.
Os tokens do Keycloak nao sao entregues pelo endpoint `/sobre` nem precisam ser
armazenados pelo frontend.

Documentacao detalhada:
[`Authentication/oauth2/README.md`](src/main/java/com/rafael/autenticacao/Authentication/oauth2/README.md).

### SAML 2.0

O Spring atua como Service Provider e o Keycloak como Identity Provider. O
Spring cria o `AuthnRequest`, recebe a `SAMLResponse`, valida protocolo,
audience, tempo e assinaturas e converte atributos confiaveis em authorities.

Depois da validacao, a identidade SAML tambem e mantida em um contexto proprio
na `HttpSession`. O laboratorio possui logout local e Single Logout.

Documentacao detalhada:
[`Authentication/saml/README.md`](src/main/java/com/rafael/autenticacao/Authentication/saml/README.md).

## Como as autenticacoes foram separadas

Esta e a principal decisao arquitetural do laboratorio.

### 1. Uma SecurityFilterChain para cada modalidade

O `FilterChainProxy` do Spring Security seleciona a primeira chain cujo
`securityMatcher` corresponde a requisicao:

| Ordem | Modalidade | Rotas selecionadas | Estado |
| --- | --- | --- | --- |
| `1` | sessao | `/auth/session/**`, `/usuarios/**` | stateful |
| `2` | JWT | `/auth/jwt/**` | stateless |
| `3` | OAuth2/OIDC | `/auth/oauth2/**` | stateful |
| `4` | SAML | `/auth/saml/**` e endpoints do protocolo SAML | stateful |
| ultima | fallback | qualquer rota restante | `denyAll()` |

Somente uma chain processa cada requisicao. Assim, cada modalidade possui suas
proprias regras de autorizacao, CORS, CSRF, handlers, login e logout.

A ordem nao indica que um mecanismo e mais seguro. Ela define apenas qual chain
tem prioridade quando os matchers sao avaliados.

### 2. Um atributo de SecurityContext para cada login stateful

Por padrao, `HttpSessionSecurityContextRepository` usa o atributo de sessao:

```text
SPRING_SECURITY_CONTEXT
```

Se sessao, OAuth2 e SAML usassem esse mesmo atributo, o ultimo login substituiria
o contexto anterior. Uma requisicao de outra modalidade tambem poderia ler uma
autenticacao que nao pertence a ela.

Para evitar isso, cada modulo possui seu proprio
`HttpSessionSecurityContextRepository`:

```text
Sessao -> SPRING_SECURITY_CONTEXT_SESSION
OAuth2 -> SPRING_SECURITY_CONTEXT_OAUTH2
SAML   -> SPRING_SECURITY_CONTEXT_SAML
```

O navegador pode possuir um unico cookie `JSESSIONID`, mas a `HttpSession`
referenciada por ele contem tres atributos independentes:

```text
JSESSIONID
    |
    +-- SPRING_SECURITY_CONTEXT_SESSION -> usuario local autenticado
    +-- SPRING_SECURITY_CONTEXT_OAUTH2  -> OidcUser autenticado
    +-- SPRING_SECURITY_CONTEXT_SAML    -> identidade SAML autenticada
```

Quando uma requisicao chega, a chain selecionada carrega somente o repository
daquela modalidade. Por exemplo, `/usuarios/**` pertence a chain de sessao e
nao considera um login OAuth2 ou SAML, mesmo que esses contextos existam na
mesma `HttpSession`.

Esse isolamento esta implementado em:

- `SecurityConfigBySession.sessionSecurityContextRepository()`;
- `SecurityConfigByOAuth2.oauth2SecurityContextRepository()`;
- `SecurityConfigBySaml.samlSecurityContextRepository()`.

O comportamento tambem possui um teste dedicado em
[`SecurityContextIsolationTest`](src/test/java/com/rafael/autenticacao/Authentication/shared/config/SecurityContextIsolationTest.java).

### 3. Logout sem destruir as outras autenticacoes

Invalidar toda a `HttpSession` apagaria os tres contextos de uma vez. Para
manter os logins independentes:

- o logout de sessao salva um contexto vazio somente no repository de sessao;
- o logout OAuth2 usa `invalidateHttpSession(false)` e remove somente o contexto
  OAuth2;
- o logout SAML usa `invalidateHttpSession(false)` e remove somente o contexto
  SAML;
- o logout JWT revoga a familia do refresh token e limpa seu cookie, sem usar
  `HttpSession`.

O logout OAuth2 ainda pode encerrar a sessao SSO no Keycloak. O SAML oferece um
endpoint separado para Single Logout. Essas sessoes do Keycloak pertencem ao
Identity Provider em `localhost:8180` e nao sao o mesmo estado que o
`JSESSIONID` da aplicacao em `localhost:8080`.

### 4. JWT permanece stateless

A chain JWT usa `SessionCreationPolicy.STATELESS`. O backend nao salva a
autenticacao JWT em `HttpSession`: cada requisicao apresenta o access token e o
Spring cria um `SecurityContext` apenas durante aquela requisicao.

O refresh token em cookie nao transforma o JWT em uma sessao do Spring. Ele e
uma credencial opaca persistida como hash para permitir rotacao e revogacao.

### 5. Fallback fechado

A ultima `SecurityFilterChain` corresponde a qualquer rota que nao tenha sido
selecionada anteriormente e aplica `denyAll()`. Externamente ela responde `404`
para nao revelar recursos internos.

Isso impede que um novo controller fique publico por acidente apenas porque seu
endpoint ainda nao foi associado a uma modalidade.

## Estrutura principal

```text
src/main/java/com/rafael/autenticacao/
|-- Authentication/
|   |-- session/     # login local stateful
|   |-- jwt/         # access e refresh token
|   |-- oauth2/      # OAuth2 Client e OIDC
|   |-- saml/        # SAML Service Provider
|   `-- shared/      # contratos e componentes compartilhados
|-- Usuario/         # dominio, repository, service e CRUD
`-- Shared/          # tratamento global de excecoes MVC

src/main/resources/
|-- application.properties
`-- db/migration/    # migrations Flyway

keycloak/            # realm reproduzivel e bootstrap local
docs/                # evolucoes futuras
```

## Pre-requisitos

- Java 21;
- PostgreSQL acessivel localmente;
- Docker com Docker Compose;
- OpenSSL;
- portas `8080`, `8180` e `5432` disponiveis, considerando os valores padrao.

Nao e necessario instalar Maven globalmente porque o projeto possui `mvnw`.

## Configuracao local

### 1. Variaveis de ambiente

Crie o arquivo local a partir do exemplo:

```bash
cp .env.exemple .env
```

Preencha principalmente:

- credenciais administrativas e do usuario de estudo do Keycloak;
- `DB_NAME`, `DB_USERNAME` e `DB_PASSWORD`;
- `OAUTH2_CLIENT_SECRET`;
- URLs do frontend quando forem diferentes de `http://localhost:5173`.

O `.env` real e ignorado pelo Git. Nao versione senhas, secrets, chaves ou
tokens.

### 2. PostgreSQL

Crie o banco indicado por `DB_NAME` antes de iniciar a aplicacao. O Flyway cria
e atualiza as tabelas, mas nao cria o banco PostgreSQL.

As migrations atuais criam:

- `usuario`;
- `jwt_refresh_token`;
- constraints, indices e ajuste do tipo do hash do refresh token.

O Hibernate usa `ddl-auto=validate`, portanto valida o schema sem altera-lo.

### 3. Credenciais SAML

Gere a chave privada e o certificado publico do Service Provider:

```bash
./scripts/generate-saml-credentials.sh
```

Os arquivos sao gravados em `.local/saml`. A chave privada permanece fora do
Git. O certificado publico e carregado no client SAML durante a importacao do
realm.

### 4. Keycloak

Inicie o container:

```bash
docker compose up -d keycloak
```

Na primeira inicializacao, o Keycloak importa o realm `autenticacao-lab` com os
clients OIDC e SAML, roles, mappers e usuario de estudo.

O import nao sobrescreve um realm que ja existe no volume. Consulte
[`keycloak/README.md`](keycloak/README.md) para detalhes.

### 5. Aplicacao

Com PostgreSQL e Keycloak disponiveis:

```bash
./mvnw spring-boot:run
```

A API fica disponivel em:

```text
http://localhost:8080
```

O Keycloak fica disponivel em:

```text
http://localhost:8180
```

## Endpoints principais

| Modalidade | Operacao | Endpoint |
| --- | --- | --- |
| sessao | CSRF | `GET /auth/session/csrf` |
| sessao | registro | `POST /auth/session/register` |
| sessao | login | `POST /auth/session/login` |
| sessao | detalhes | `GET /auth/session/sobre` |
| sessao | logout | `POST /auth/session/logout` |
| JWT | CSRF | `GET /auth/jwt/csrf` |
| JWT | login | `POST /auth/jwt/login` |
| JWT | detalhes | `GET /auth/jwt/sobre` |
| JWT | refresh | `POST /auth/jwt/refresh` |
| JWT | logout | `POST /auth/jwt/logout` |
| OAuth2/OIDC | iniciar login | `GET /auth/oauth2/authorization/keycloak` |
| OAuth2/OIDC | detalhes | `GET /auth/oauth2/sobre` |
| OAuth2/OIDC | CSRF | `GET /auth/oauth2/csrf` |
| OAuth2/OIDC | logout | `POST /auth/oauth2/logout` |
| SAML | iniciar login | `GET /saml2/authenticate/keycloak-saml` |
| SAML | metadata do SP | `GET /saml2/metadata/keycloak-saml` |
| SAML | detalhes | `GET /auth/saml/sobre` |
| SAML | CSRF | `GET /auth/saml/csrf` |
| SAML | logout local | `POST /auth/saml/logout` |
| SAML | Single Logout | `POST /auth/saml/slo` |

Todos os endpoints `/sobre` retornam uma representacao controlada da identidade
e suas roles. Eles nao retornam senha, refresh token, ID Token, access token do
Keycloak ou assertion SAML.

Os endpoints mutaveis protegidos por cookie exigem o token CSRF pertencente a
mesma sessao. Os READMEs de cada modulo mostram os fluxos e headers corretos.

## Testes

Execute toda a suite com:

```bash
./mvnw test
```

Os testes cobrem services, tokens, refresh token, controllers, CORS, CSRF,
handlers, autorizacao, selecao das chains e isolamento dos contextos de
autenticacao.

## Documentacao por assunto

- [Sessao HTTP](src/main/java/com/rafael/autenticacao/Authentication/session/README.md)
- [JWT](src/main/java/com/rafael/autenticacao/Authentication/jwt/README.md)
- [OAuth 2.0 e OpenID Connect](src/main/java/com/rafael/autenticacao/Authentication/oauth2/README.md)
- [SAML 2.0](src/main/java/com/rafael/autenticacao/Authentication/saml/README.md)
- [Keycloak local](keycloak/README.md)
- [Atualizacoes futuras](docs/ATUALIZACOES_FUTURAS.md)
