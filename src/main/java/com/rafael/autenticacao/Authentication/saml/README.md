# Autenticacao SAML 2.0

## Objetivo

Esta modalidade demonstra autenticacao federada SAML 2.0 usando:

- Spring Security como Service Provider, tambem chamado Relying Party;
- Keycloak como Identity Provider, tambem chamado Asserting Party;
- OpenSAML para criar, interpretar e validar mensagens do protocolo;
- sessao HTTP para manter o usuario autenticado depois do login.

O SAML nao envia senha para a aplicacao. A senha e validada pelo Identity
Provider e a aplicacao recebe uma assertion assinada contendo a identidade do
usuario.

## Responsabilidades

### Service Provider

A aplicacao Spring e responsavel por:

- iniciar o login com um `AuthnRequest`;
- informar seu Entity ID e seu Assertion Consumer Service;
- validar issuer, audience, destino, tempo e assinatura da resposta;
- transformar atributos SAML em dados de identidade e authorities;
- criar e remover seu contexto de autenticacao local;
- iniciar ou receber mensagens de Single Logout.

### Identity Provider

O Keycloak e responsavel por:

- autenticar o usuario;
- emitir a `SAMLResponse` e a assertion;
- assinar os documentos enviados ao Service Provider;
- publicar metadata e certificados de verificacao;
- enviar atributos e roles configurados nos protocol mappers;
- manter e encerrar sua propria sessao de SSO.

## Elementos principais

- **Entity ID:** identificador unico da parte SAML. O Service Provider usa
  `autenticacao-lab-saml`.
- **Metadata:** XML que publica endpoints, bindings e certificados de uma
  parte.
- **AuthnRequest:** solicitacao de autenticacao criada pelo Spring.
- **SAMLResponse:** resposta enviada pelo Keycloak para o ACS.
- **Assertion:** declaracao assinada sobre identidade, atributos e contexto de
  autenticacao.
- **ACS:** endpoint que recebe a resposta de login.
- **NameID:** identificador do usuario dentro do protocolo.
- **Binding:** forma de transportar uma mensagem SAML. Este laboratorio usa
  Redirect para iniciar o login e POST para receber respostas.
- **Single Logout:** coordenacao do encerramento das sessoes do Service
  Provider e do Identity Provider.

## Fluxo de login

1. O navegador acessa `/saml2/authenticate/keycloak-saml`.
2. O Spring cria e assina um `AuthnRequest`.
3. O navegador e redirecionado para o endpoint SAML do Keycloak.
4. O Keycloak autentica o usuario ou reutiliza sua sessao de SSO.
5. O Keycloak envia uma `SAMLResponse` assinada por POST ao ACS.
6. O Spring valida a resposta e sua assertion usando o certificado do metadata
   do Keycloak.
7. As roles conhecidas sao convertidas para authorities do Spring Security.
8. O Spring cria a sessao local e redireciona para
   `SAML_FRONTEND_SUCCESS_URL`.
9. A pagina do frontend consulta `/auth/saml/sobre` com
   `credentials: "include"`.

Uma resposta apenas decodificada nao e confiavel. A identidade somente e aceita
depois das validacoes criptograficas e de protocolo executadas pelo Spring
Security e pelo OpenSAML.

## Endpoints

| Metodo | Endpoint | Funcao |
| --- | --- | --- |
| GET | `/saml2/authenticate/keycloak-saml` | Inicia o login SAML |
| POST | `/login/saml2/sso/keycloak-saml` | ACS que recebe a resposta do Keycloak |
| GET | `/saml2/metadata/keycloak-saml` | Publica o metadata do Service Provider |
| GET | `/auth/saml/sobre` | Retorna os dados selecionados do usuario autenticado |
| GET | `/auth/saml/csrf` | Emite o token CSRF usado nos logouts iniciados pelo usuario |
| POST | `/auth/saml/logout` | Encerra somente a sessao local |
| POST | `/auth/saml/slo` | Inicia Single Logout no Keycloak |
| POST | `/logout/saml2/slo` | Recebe LogoutRequest ou LogoutResponse SAML |
| GET | `/auth/saml/logged-out` | Confirma o logout local |

Nao existem endpoints `/user` e `/admin`. O endpoint `/sobre` devolve as
authorities e a camada de apresentacao escolhe a pagina correspondente. Recursos
de negocio futuros devem aplicar suas proprias regras de autorizacao no backend.

## Estrutura da implementacao

### `SecurityConfigBySaml`

Cria uma `SecurityFilterChain` exclusiva, com ordem posterior as chains de
sessao, JWT e OAuth 2.0. Ela delimita os endpoints SAML, configura login,
metadata, sessao, handlers e os dois tipos de logout.

### `SamlResponseAuthenticationConverter`

Delega primeiro ao conversor oficial do Spring. Depois substitui a role generica
padrao pelas authorities produzidas a partir dos atributos confiaveis da
assertion. A authority que identifica o fator SAML e preservada.

### `SamlAuthoritiesMapper`

Le o atributo `Role` enviado pelo client scope `role_list` do Keycloak. Apenas
roles existentes no enum de dominio sao aceitas:

- `USER` vira `ROLE_USER`;
- `ADMIN` vira `ROLE_ADMIN`;
- roles internas, desconhecidas ou arbitrarias sao descartadas.

Essa allowlist impede que uma configuracao incorreta do Identity Provider crie
permissoes nao reconhecidas pela aplicacao.

### `AuthBySamlController`

O controller nao recebe senhas nem processa XML. Ele apenas apresenta dados ja
validados pelo Spring Security: `NameID`, username, nome, email, registration ID
e authorities. A assertion completa e a resposta SAML nao sao devolvidas.

### Handlers

- falhas ao validar uma resposta SAML retornam `401` com `ProblemDetail`;
- sessao SAML ausente ou expirada em `/sobre` retorna `401` com
  `ProblemDetail`;
- acesso negado para um usuario autenticado retorna `403` com `ProblemDetail`;
- detalhes internos e mensagens criptograficas nao sao expostos ao cliente.

## Configuracao do Keycloak

O client `autenticacao-lab-saml` possui:

- protocolo `saml`;
- Entity ID igual ao Client ID;
- ACS `http://localhost:8080/login/saml2/sso/keycloak-saml`;
- resposta e assertion assinadas;
- validacao da assinatura dos documentos enviados pelo Service Provider;
- POST binding forcado para respostas;
- `NameID` baseado no username;
- endpoint de logout `http://localhost:8080/logout/saml2/slo`;
- mappers para username, email, firstName e lastName;
- client scope `role_list`, que publica as roles no atributo `Role`.

O usuario de teste `rafael` possui a realm role `USER`.

## Chaves e assinaturas

O Keycloak e o Spring possuem responsabilidades criptograficas diferentes:

- o certificado publico do Keycloak vem do metadata do realm e valida respostas
  e assertions;
- a chave privada do Service Provider assina AuthnRequests, LogoutRequests e
  LogoutResponses;
- somente o certificado publico do Service Provider foi importado no Keycloak;
- a chave privada fica em `.local/saml` e esta ignorada pelo Git.

As credenciais locais podem ser geradas com:

```bash
./scripts/generate-saml-credentials.sh
```

A chave usa RSA com 3072 bits e o certificado de laboratorio e autoassinado.
Em producao, a chave deve ficar em secret manager, cofre de chaves ou volume
protegido. A rotacao exige publicar o novo certificado no Keycloak antes de
remover o anterior.

## Sessao, CSRF e CORS

SAML Web Browser SSO resulta em uma sessao HTTP local. O cookie de sessao nao
contem a assertion; ele apenas identifica a sessao mantida pelo servidor.

O ACS e os callbacks de SLO precisam receber POSTs do Identity Provider. O
Spring Security trata esses endpoints dentro dos filtros SAML. Ja os logouts
iniciados pelo usuario exigem CSRF porque alteram o estado da sessao.

O fluxo de redirecionamento SAML nao depende de CORS. Entretanto, a interface
em outra origem consulta `/sobre`, `/csrf` e inicia logout por JavaScript. Por
isso, `SamlCorsConfig` permite somente `SAML_FRONTEND_ORIGIN`, os metodos
`GET`, `POST` e `OPTIONS`, os headers necessarios e credenciais. A politica vale
apenas para `/auth/saml/**`; chamadas cross-origin devem usar
`credentials: "include"`.

## Logout local e Single Logout

O logout local remove somente o contexto SAML salvo sob a chave
`SPRING_SECURITY_CONTEXT_SAML`. Os contextos da sessao tradicional e do OAuth2
permanecem independentes. A sessao do Keycloak pode continuar ativa e permitir
um novo login sem solicitar senha.

O Single Logout tambem envia uma mensagem SAML assinada ao Keycloak. O Keycloak
encerra sua sessao e devolve uma resposta assinada para o endpoint SLO do
Service Provider.

Os dois endpoints sao separados para deixar explicita a diferenca entre limpar
somente o estado local e coordenar o logout federado.

Depois do logout, `/auth/saml/logged-out` funciona como bridge e redireciona o
navegador para `SAML_FRONTEND_LOGOUT_URL`.

## Usuarios locais

Esta modalidade nao cria nem atualiza registros na tabela `usuario`. O Keycloak
e a fonte de identidade e roles. Essa decisao evita sincronizacao implicita e
mantem o laboratorio focado no protocolo.

Um sistema de negocio poderia vincular o `NameID` ou outro identificador estavel
a um cadastro local. Essa vinculacao deve ser explicita e nao deve usar email
mutavel como unica chave sem uma politica definida.

## Testes

Os testes automatizados cobrem:

- inicio do fluxo e geracao do AuthnRequest;
- publicacao do metadata do Service Provider;
- protecao do endpoint `/sobre`;
- exposicao somente dos atributos selecionados;
- allowlist e deduplicacao de roles;
- emissao de token CSRF;
- rejeicao dos logouts sem CSRF;
- remocao isolada do contexto SAML no logout local;
- resposta `401` quando a sessao esta ausente ou expirada;
- redirecionamento final de logout para o frontend;
- CORS para origem permitida e rejeicao de origem desconhecida;
- respostas `ProblemDetail` dos handlers.

O teste completo com credenciais reais depende do Keycloak e do PostgreSQL em
execucao. Ele deve confirmar login, atributos, roles, assinatura e os dois tipos
de logout.

## Pontos de atencao

- nunca implementar um parser XML ou validador de assinatura manual;
- nunca confiar em atributos antes da validacao completa da assertion;
- restringir roles externas a uma allowlist do dominio;
- manter Entity ID, ACS e URLs de logout exatamente iguais nos dois lados;
- proteger chaves privadas e planejar sua rotacao;
- manter relogios do Service Provider e do Identity Provider sincronizados;
- usar HTTPS fora do ambiente local;
- limitar logs para nao registrar assertions ou dados pessoais completos;
- considerar disponibilidade do metadata, pois a aplicacao o consulta durante
  a inicializacao.

## Quando SAML se encaixa melhor

SAML e comum em sistemas corporativos, portais internos, integracoes B2B,
universidades e ambientes que usam Identity Providers como Keycloak, ADFS,
Okta ou Entra ID.

Para APIs stateless e aplicativos moveis, OAuth 2.0 e OpenID Connect normalmente
sao mais adequados. SAML e orientado ao navegador, XML, redirecionamentos e
sessoes federadas.
