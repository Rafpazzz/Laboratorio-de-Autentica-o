# Autenticacao e autorizacao com OAuth 2.0 e OpenID Connect

Este modulo demonstra login federado com Spring Security e Keycloak. O fluxo
implementado usa Authorization Code com PKCE, OpenID Connect, sessao local no
Spring, autorizacao por roles, CSRF, CORS e RP-Initiated Logout.

O objetivo e estudar o protocolo e as responsabilidades de cada componente.
O Keycloak autentica o usuario e emite tokens. A aplicacao valida a identidade,
converte roles externas em authorities locais e mantem o login do navegador em
uma `HttpSession`.

## 1. OAuth 2.0 e OpenID Connect nao sao a mesma coisa

OAuth 2.0 e um framework de autorizacao. Ele permite que um cliente obtenha
acesso limitado a recursos sem receber a senha do usuario.

OAuth 2.0 puro nao define uma forma padronizada de responder perguntas como:

- quem e o usuario autenticado;
- qual identificador representa essa pessoa;
- quando e como ela foi autenticada;
- como validar os dados de identidade recebidos.

OpenID Connect, ou OIDC, adiciona uma camada de identidade sobre OAuth 2.0. Ele
define o ID Token, claims de identidade, discovery, UserInfo, nonce, logout e
outras regras necessarias para login federado.

Neste laboratorio:

- OAuth 2.0 fornece o Authorization Code Flow;
- OIDC fornece a autenticacao e o ID Token;
- Keycloak atua como Authorization Server e OpenID Provider;
- Spring Boot atua como OAuth2 Client e OIDC Relying Party;
- o navegador mantem apenas a sessao local da aplicacao.

## 2. Atores do fluxo

### Resource Owner

E o usuario que possui a identidade e autoriza o login.

### User Agent

E normalmente o navegador. Ele navega entre a aplicacao e o Keycloak, carrega
a tela de login e transporta redirecionamentos e cookies.

### OAuth2 Client

E a aplicacao Spring. Ela inicia o login, recebe o authorization code e o troca
por tokens usando uma comunicacao direta com o Keycloak.

O termo `client` neste contexto nao significa necessariamente frontend. O
backend Spring e o client registrado no Keycloak.

### Authorization Server e OpenID Provider

O Keycloak autentica o usuario, solicita consentimento quando aplicavel, emite
codes e tokens, publica chaves e mantem a sessao SSO.

### Resource Server

E uma API que aceita access tokens para proteger recursos. O modulo OAuth2
atual nao usa o access token do Keycloak como credencial das rotas locais. As
rotas usam a sessao criada pelo `oauth2Login`.

O modulo JWT do laboratorio e um exemplo separado de Resource Server.

## 3. Modelo adotado no laboratorio

O modelo atual e semelhante a um backend for frontend:

1. O navegador inicia o login pelo backend.
2. O backend redireciona para o Keycloak.
3. O Keycloak autentica o usuario.
4. O backend recebe o authorization code.
5. O backend troca o code por tokens.
6. O Spring valida a resposta OIDC.
7. O backend cria uma sessao local.
8. O navegador recebe somente o cookie `JSESSIONID`.
9. As requisicoes seguintes sao autorizadas pela sessao local.

Os tokens do provedor nao sao devolvidos pelo endpoint `/sobre` e nao precisam ser
armazenados em `localStorage` ou `sessionStorage`.

Essa escolha reduz a exposicao de tokens ao JavaScript e reutiliza as protecoes
de sessao do Spring Security. Como consequencia, requisicoes que alteram estado
precisam de protecao CSRF.

## 4. Por que Keycloak em vez do OAuth do Google

Google e uma boa opcao para login social, mas Keycloak atende melhor ao objetivo
didatico deste laboratorio.

Com Keycloak e possivel controlar localmente:

- usuarios e credenciais;
- realms e isolamento entre ambientes;
- clients publicos e confidenciais;
- redirect URIs e post logout redirect URIs;
- realm roles e client roles;
- scopes e protocol mappers;
- conteudo de ID tokens e access tokens;
- expiracao, sessoes, MFA e politicas de autenticacao;
- chaves de assinatura e endpoints OIDC;
- erros de configuracao e cenarios de teste.

Isso permite estudar tanto a aplicacao cliente quanto o provedor. Com Google,
grande parte dessas decisoes pertence a um servico externo e as roles internas
da aplicacao ainda precisariam de outra fonte.

Keycloak tambem pode executar localmente sem depender de contas pessoais,
internet ou configuracoes de consentimento de uma plataforma de terceiros.

Google pode ser adicionado futuramente como um segundo provider. O Spring
Security permite registrar varios providers sem substituir o Keycloak.

## 5. Authorization Code Flow com PKCE

O Authorization Code Flow evita entregar tokens diretamente pela URL do
navegador. O callback recebe um code curto e de uso unico. A troca por tokens e
feita pelo backend diretamente no token endpoint.

PKCE adiciona dois valores:

- `code_verifier`: segredo aleatorio mantido pelo client durante o fluxo;
- `code_challenge`: derivacao do verifier enviada no pedido de autorizacao.

Na troca do code, o client apresenta o verifier. O servidor compara sua
derivacao com o challenge original. Um invasor que obtenha somente o code nao
consegue troca-lo sem o verifier.

O Spring Security gera e valida essa parte do fluxo. A URL de autorizacao
produzida no laboratorio usa `code_challenge_method=S256`.

## 6. Controles de correlacao do login

### state

`state` relaciona a resposta do provedor com a solicitacao iniciada pela
aplicacao. Ele protege principalmente contra respostas injetadas e CSRF no
fluxo OAuth.

### nonce

`nonce` relaciona o ID Token ao login OIDC que o originou. Ele reduz o risco de
reutilizacao de um ID Token em outro fluxo.

### redirect URI

A redirect URI informa onde o provedor pode devolver o navegador. Ela deve ser
registrada de forma exata no Keycloak:

```text
http://localhost:8080/auth/oauth2/callback/keycloak
```

Wildcards amplos aumentam a superficie para vazamento de codes e nao devem ser
usados sem necessidade.

## 7. Artefatos recebidos do Keycloak

### Authorization code

E curto, temporario e de uso unico. Ele nao representa uma sessao e nao deve
ser usado para chamar APIs.

### ID Token

E um JWT destinado ao client. Ele declara a identidade autenticada e contem
claims como `sub`, `preferred_username`, `name`, `email`, `iss`, `aud`, `iat` e
`exp`.

O ID Token nao e um access token. Seu destinatario e a aplicacao cliente, nao
uma API arbitraria.

### Access token

Autoriza chamadas a recursos protegidos pelo provedor ou por um Resource
Server que confie naquele emissor. O fluxo atual nao entrega esse token ao
frontend e nao o usa como credencial das rotas `/auth/oauth2/**`.

### Refresh token do provedor

Pode permitir renovacao sem novo login, dependendo dos scopes e da politica do
provider. Este laboratorio ainda nao solicita `offline_access` nem implementa
uma politica propria para refresh tokens do Keycloak.

### JSESSIONID

E o identificador opaco da sessao local do Spring. Depois do callback, ele e a
credencial utilizada pelo navegador nas rotas deste modulo.

## 8. Discovery e validacao do provedor

A propriedade `issuer-uri` aponta para:

```text
http://localhost:8180/realms/autenticacao-lab
```

A partir desse issuer, o Spring consulta o documento OIDC Discovery. Ele
descobre endpoints como:

- authorization endpoint;
- token endpoint;
- UserInfo endpoint;
- JWKS endpoint;
- end session endpoint.

O JWKS publica as chaves publicas usadas para validar tokens assinados pelo
Keycloak. A aplicacao deve confiar em um issuer fixo e esperado; nao deve
aceitar um issuer fornecido pelo usuario.

## 9. Configuracao do Keycloak

### Realm

O realm `autenticacao-lab` isola usuarios, roles, clients e politicas deste
laboratorio.

### Client

O client `autenticacao-lab-client` e confidencial. O backend consegue proteger
um client secret, diferentemente de uma SPA executada integralmente no
navegador.

Configuracoes relevantes:

- Standard Flow habilitado;
- redirect URI exata para o callback;
- post logout redirect URI exata;
- client secret mantido fora do repositorio;
- protocolo `openid-connect`.

### Roles

O realm possui as roles de aplicacao `USER` e `ADMIN`. O usuario `rafael`
recebe `USER` para permitir o teste de acesso comum e de negacao administrativa.

### Protocol mapper

O mapper `realm roles` publica roles em:

```json
{
  "realm_access": {
    "roles": ["USER"]
  }
}
```

Para o mapper usado pela aplicacao, essa claim precisa estar disponivel no ID
Token ou no UserInfo. O access token do Keycloak tambem pode conter a claim,
mas `GrantedAuthoritiesMapper` nao recebe diretamente esse token.

## 10. Configuracao da aplicacao

Variaveis locais:

```text
OAUTH2_CLIENT_ID
OAUTH2_CLIENT_SECRET
OAUTH2_ISSUER_URI
OAUTH2_FRONTEND_ORIGIN
OAUTH2_FRONTEND_SUCCESS_URL
OAUTH2_FRONTEND_LOGOUT_URL
```

O `.env` e importado como arquivo de propriedades no desenvolvimento local. O
arquivo real nao deve ser versionado. `.env.exemple` documenta apenas nomes e
valores ilustrativos.

Principais propriedades:

```properties
spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email
spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/auth/oauth2/callback/{registrationId}
```

O scope `openid` ativa o comportamento OIDC. Sem ele, o Spring trata o provider
como OAuth 2.0 e nao usa o processamento especifico de ID Token.

## 11. Contrato HTTP atual

| Metodo | Rota | Regra |
|---|---|---|
| `GET` | `/auth/oauth2/authorization/keycloak` | inicia o login |
| `GET` | `/auth/oauth2/callback/keycloak` | callback processado pelo filtro |
| `GET` | `/auth/oauth2/sobre` | exige autenticacao |
| `GET` | `/auth/oauth2/csrf` | emite token CSRF |
| `POST` | `/auth/oauth2/logout` | exige sessao e token CSRF |
| `GET` | `/auth/oauth2/logged-out` | retorno publico do logout |

O callback e o logout sao interceptados por filtros do Spring Security. Nao ha
necessidade de implementar esses protocolos manualmente em um controller.

## 12. Fluxo completo do login

1. O navegador acessa `/auth/oauth2/authorization/keycloak`.
2. `OAuth2AuthorizationRequestRedirectFilter` cria state, nonce e PKCE.
3. O pedido e salvo na sessao para ser correlacionado depois.
4. O navegador e redirecionado ao authorization endpoint.
5. O Keycloak autentica o usuario.
6. O Keycloak redireciona para o callback com code e state.
7. `OAuth2LoginAuthenticationFilter` recebe o callback.
8. O Spring valida state e troca o code no token endpoint.
9. O ID Token e validado contra issuer, audience, assinatura e tempo.
10. O Spring cria um `OidcUser` com claims e authorities iniciais.
11. O mapper converte roles permitidas do Keycloak.
12. Um `OAuth2AuthenticationToken` autenticado e criado.
13. O `SecurityContext` e salvo na `HttpSession`.
14. O navegador recebe `JSESSIONID`.
15. O usuario e redirecionado para `OAUTH2_FRONTEND_SUCCESS_URL`.
16. A pagina do frontend consulta `/auth/oauth2/sobre` com
    `credentials: "include"` para obter a identidade validada.

## 13. Mapeamento seguro de roles

`KeycloakRealmRoleAuthoritiesMapper` le `realm_access.roles` e aplica uma
allowlist explicita:

- `USER` vira `ROLE_USER`;
- `ADMIN` vira `ROLE_ADMIN`.

Roles internas como `offline_access`, `uma_authorization` e roles desconhecidas
nao se tornam permissoes da aplicacao.

A allowlist e importante porque o provedor controla as claims recebidas. Uma
mudanca administrativa no Keycloak nao deve conceder automaticamente uma nova
permissao local sem uma decisao no codigo.

As authorities OIDC originais, como `OIDC_USER` e `SCOPE_*`, continuam no
contexto interno. O endpoint `/sobre` entrega ao frontend apenas authorities
com prefixo `ROLE_`.

## 14. Autenticacao e autorizacao

Autenticacao responde quem e o usuario. O login OIDC e o ID Token resolvem essa
parte.

Autorizacao responde o que o usuario pode fazer. A rota que inicia o login
redireciona o navegador para o Keycloak. Ja uma chamada anonima a `/sobre`
recebe `401` em `ProblemDetail`, permitindo que o frontend decida quando iniciar
um novo login. O endpoint autenticado entrega somente as roles permitidas ao
frontend. Recursos de negocio futuros devem validar essas mesmas roles no
backend.

O prefixo `ROLE_` e a convencao esperada por `hasRole` no Spring Security.

## 15. Sessao local e sessao SSO

Depois do login existem duas sessoes independentes:

- sessao local da aplicacao, identificada por `JSESSIONID`;
- sessao SSO do Keycloak, identificada pelos cookies do proprio Keycloak.

O `JSESSIONID` pode carregar contextos independentes do laboratorio. O contexto
OAuth2 usa a chave `SPRING_SECURITY_CONTEXT_OAUTH2`; sessao tradicional e SAML
usam outras chaves. O logout OAuth2 remove somente seu proprio contexto e ainda
encerra o SSO no Keycloak.

Essa diferenca e o motivo para usar logout iniciado pelo client OIDC.

## 16. CSRF

O navegador envia `JSESSIONID` automaticamente. Sem CSRF, um site malicioso
poderia induzir o navegador autenticado a enviar uma requisicao que altera
estado.

O logout usa `POST` e exige CSRF. O token e obtido por:

```text
GET /auth/oauth2/csrf
```

O cliente devolve o valor no header:

```text
X-CSRF-TOKEN: valor-do-token
```

O token deve pertencer a mesma sessao do `JSESSIONID`. `permitAll` em uma rota
nao desativa o `CsrfFilter`.

`state` protege o fluxo OAuth e o token CSRF protege requisicoes locais. Um nao
substitui o outro.

## 17. CORS

`OAuth2CorsConfig` aplica CORS somente a `/auth/oauth2/**`.

Configuracao atual:

- origem: `OAUTH2_FRONTEND_ORIGIN`;
- valor local padrao: `http://localhost:5173`;
- metodos: `GET`, `POST` e `OPTIONS`;
- headers: `Content-Type` e `X-CSRF-TOKEN`;
- credenciais: habilitadas;
- cache de preflight: uma hora.

Credenciais sao necessarias porque o frontend precisa enviar `JSESSIONID`. No
JavaScript, requisicoes cross-origin devem usar `credentials: "include"`.

Quando `allowCredentials` esta habilitado, nao se deve usar `*` como origem.

O header `Authorization` nao foi permitido porque este fluxo nao usa bearer
token no navegador. Adicionar headers sem necessidade amplia o contrato da API.

CORS e uma politica aplicada pelo navegador. Ele nao autentica usuarios, nao
protege clientes HTTP fora do navegador e nao substitui CSRF.

O login e o logout OIDC envolvem navegacao entre origens. O redirecionamento de
logout deve ser tratado como navegacao do navegador, e nao como uma chamada
`fetch` que espera consumir a pagina do Keycloak via CORS.

## 18. Logout OIDC

O endpoint local e:

```text
POST /auth/oauth2/logout
```

O `LogoutFilter` executa antes do controller e realiza:

1. validacao do token CSRF;
2. limpeza da autenticacao;
3. remocao do contexto OAuth2 da `HttpSession`;
4. preservacao dos contextos de sessao tradicional e SAML;
5. chamada ao `OidcClientInitiatedLogoutSuccessHandler`;
6. redirecionamento ao `end_session_endpoint` do Keycloak;
7. envio de `id_token_hint`;
8. envio de `post_logout_redirect_uri`;
9. retorno para `/auth/oauth2/logged-out`;
10. redirecionamento final para `OAUTH2_FRONTEND_LOGOUT_URL`.

O `id_token_hint` ajuda o provider a identificar a sessao que deve ser
encerrada. A post logout redirect URI precisa estar previamente cadastrada no
client do Keycloak.

## 19. SecurityFilterChain dedicada

Este modulo usa uma chain com `@Order(3)` e matcher exclusivo:

```text
/auth/oauth2/**
```

As chains anteriores tratam sessao tradicional e JWT. O Spring seleciona a
primeira chain cujo matcher aceita a requisicao. A ordem nao representa nivel
de seguranca; representa prioridade de selecao.

A separacao permite que cada mecanismo tenha suas proprias regras de sessao,
CSRF, CORS, handlers e endpoints sem misturar modelos incompativeis.

Uma chain final, com menor prioridade, aplica `denyAll` a toda rota que nao
pertenca explicitamente a outro mecanismo. A resposta externa e `404` para nao
publicar detalhes sobre recursos internos, mas a decisao de seguranca continua
sendo negar o acesso.

## 20. Componentes do modulo

| Componente | Responsabilidade |
|---|---|
| `SecurityConfigByOAuth2` | login, logout, rotas e autorizacao |
| `OAuth2CorsConfig` | politica CORS exclusiva do modulo |
| `KeycloakRealmRoleAuthoritiesMapper` | converte realm roles permitidas |
| `AuthByOAuth2Controller` | identidade, roles, CSRF e retorno do logout |
| `OidcUserResponseDTO` | resposta minima e controlada de identidade |
| `OAuth2AuthenticationEntryPoint` | resposta `401` para sessao ausente ou expirada |
| `OAuth2AccessDeniedHandler` | resposta `403` para usuario sem permissao |
| `ClientRegistration` | contrato do client com o provider |
| `OidcClientInitiatedLogoutSuccessHandler` | inicia logout no provider |

## 21. Fases recomendadas de implementacao

### Fase 1: definir o objetivo

Decidir se a aplicacao precisa apenas de autorizacao OAuth, login OIDC ou ambos.

### Fase 2: escolher os papeis da arquitetura

Identificar client, provider, Resource Server, navegador e local de armazenamento
dos tokens.

### Fase 3: registrar o client

Definir client type, secret, redirect URIs, post logout URIs e scopes minimos.

### Fase 4: configurar discovery

Fixar um issuer confiavel e validar a comunicacao com discovery e JWKS.

### Fase 5: implementar Authorization Code com PKCE

Delegar state, nonce, PKCE, callback e token exchange a uma biblioteca madura.

### Fase 6: validar identidade

Validar assinatura, issuer, audience, expiracao e nonce antes de confiar no ID
Token.

### Fase 7: criar a sessao da aplicacao

Definir cookies, expiracao, session fixation, CSRF e estrategia de persistencia.

### Fase 8: mapear autorizacao

Aplicar allowlist de roles e proteger recursos com regras explicitas.

### Fase 9: configurar CORS

Permitir somente origens, metodos e headers exigidos pelo frontend real.

### Fase 10: implementar logout completo

Encerrar sessao local e SSO e restringir a URI de retorno.

### Fase 11: tratar falhas

Criar handlers para erros do callback e acesso negado sem expor tokens ou
detalhes internos.

### Fase 12: testar e preparar operacao

Cobrir ataques ao state, nonce, redirect URI, roles, sessoes, chaves e falhas do
provider.

## 22. Erros e respostas

A rota de inicio do login continua produzindo o redirecionamento para o
Keycloak. Nas rotas da API, sessao ausente ou expirada retorna `401` e usuario
autenticado sem permissao retorna `403`. As duas respostas usam
`application/problem+json`.

Uma falha durante o callback OIDC ainda e responsabilidade dos filtros OAuth2 e
nao passa automaticamente pelo `GlobalExceptionHandler` do MVC. Um failure
handler especifico para esse callback permanece como melhoria futura.

Mensagens ao cliente nao devem incluir authorization code, access token, ID
Token, client secret, stack trace ou resposta completa do provider.

## 23. Teste manual

### Login

Abra no navegador:

```text
http://localhost:8080/auth/oauth2/authorization/keycloak
```

Depois do login, o callback redireciona para
`http://localhost:5173/oauth2/sobre`. Essa pagina consulta o endpoint backend
`/auth/oauth2/sobre` incluindo o cookie de sessao.

### Usuario autenticado

```text
GET http://localhost:8080/auth/oauth2/sobre
```

A resposta deve conter identidade selecionada e `ROLE_USER`, sem tokens.

### Logout

Obtenha um token da mesma sessao:

```text
GET http://localhost:8080/auth/oauth2/csrf
```

Envie:

```text
POST http://localhost:8080/auth/oauth2/logout
X-CSRF-TOKEN: valor-do-token
```

Nao existe body. O navegador passa pelo Keycloak, retorna ao bridge backend
`/auth/oauth2/logged-out` e termina em `OAUTH2_FRONTEND_LOGOUT_URL`.

## 24. Cobertura automatizada

Os testes atuais verificam:

- inicio do Authorization Code Flow;
- scopes, state, nonce e callback;
- exposicao minima de claims;
- resposta `401` previsivel para sessao ausente ou expirada;
- resposta `403` padronizada para acesso negado;
- entrega somente das roles reconhecidas no endpoint `/sobre`;
- allowlist e tolerancia a claims malformadas;
- emissao de CSRF;
- rejeicao de logout sem CSRF;
- remocao isolada do contexto OAuth2 no logout;
- construcao do logout no provider;
- CORS para origem permitida;
- rejeicao de origem desconhecida;
- isolamento da politica CORS por rota.
- bloqueio por fallback de rotas sem uma chain proprietaria.

Os testes usam um `ClientRegistration` local e nao dependem do Keycloak real.
Testes de navegador contra o container continuam importantes para validar a
integracao completa.

## 25. Sistemas em que esse modelo se encaixa

### Aplicacoes web server-side

O backend controla o login e mantem a sessao. Tokens do provider permanecem no
servidor.

### Backend for frontend

Uma SPA conversa com um backend de mesma organizacao usando cookie de sessao,
enquanto o backend executa o protocolo OIDC.

### SSO corporativo

Varios sistemas confiam no mesmo provider e reutilizam identidade, MFA e
politicas centralizadas.

### Sistemas com varios provedores

O Spring pode registrar Keycloak, Google e outros providers. A aplicacao deve
definir regras de vinculacao de contas e identidade canonica.

## 26. Quando usar outro modelo

Para APIs consumidas diretamente por mobile, terceiros ou outros servicos, um
Resource Server com bearer access token pode ser mais apropriado.

Para integracao maquina a maquina, login de usuario e sessao de navegador nao
fazem sentido. Client Credentials ou identidade de workload costuma ser mais
adequado.

Para uma aplicacao monolitica pequena sem SSO nem provedores externos, login por
sessao com credenciais locais pode ser mais simples.

## 27. Pontos de atencao

- Nunca implementar OAuth ou OIDC por manipulacao manual de URLs e JWTs.
- Nunca confiar em claims antes de validar o token.
- Nunca enviar client secret ao frontend.
- Nunca expor ID Token ou access token sem necessidade.
- Nunca usar wildcard amplo em redirect URI de producao.
- Nunca registrar codes, tokens, cookies ou secrets em logs.
- Nao confundir ID Token com access token.
- Nao usar implicit flow em novos sistemas.
- Nao desativar state, nonce, PKCE ou CSRF para contornar erros.
- Nao transformar toda role externa em permissao local automaticamente.
- Nao considerar logout local equivalente a logout SSO.
- Nao usar o modo `start-dev` do Keycloak em producao.

## 28. Estado atual e proximos limites

O fluxo essencial do laboratorio esta funcional:

- login OIDC;
- Authorization Code com PKCE;
- validacao pelo Spring Security;
- sessao local;
- roles do Keycloak;
- autorizacao `USER` e `ADMIN`;
- CSRF;
- CORS;
- logout local e SSO;
- redirecionamentos finais para o frontend;
- respostas `401` e `403` em `ProblemDetail`;
- fallback `denyAll` para rotas sem mecanismo definido;
- testes automatizados.

Ainda faltam a interface web, um handler especializado para falhas do callback
OIDC e os endurecimentos de producao registrados em
`docs/ATUALIZACOES_FUTURAS.md`.

## 29. Referencias principais

- OAuth 2.0: <https://www.rfc-editor.org/rfc/rfc6749>
- OAuth 2.0 Security Best Current Practice: <https://www.rfc-editor.org/rfc/rfc9700>
- PKCE: <https://www.rfc-editor.org/rfc/rfc7636>
- OpenID Connect Core: <https://openid.net/specs/openid-connect-core-1_0.html>
- OIDC Discovery: <https://openid.net/specs/openid-connect-discovery-1_0.html>
- RP-Initiated Logout: <https://openid.net/specs/openid-connect-rpinitiated-1_0.html>
- Spring Security OAuth2 Login: <https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html>
- Spring Security authority mapping: <https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html>
- Keycloak Server Administration Guide: <https://www.keycloak.org/docs/latest/server_admin/>
- Keycloak Securing Applications Guide: <https://www.keycloak.org/securing-apps/oidc-layers>
