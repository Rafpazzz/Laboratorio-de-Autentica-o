# Autenticacao e autorizacao por sessao

Este documento registra a implementacao de autenticacao por sessao deste laboratorio e explica as decisoes de seguranca envolvidas.

O objetivo nao e apenas descrever endpoints. O foco e entender:

- como o Spring Security valida email e senha;
- como a identidade autenticada e mantida entre requisicoes;
- por que o navegador recebe um `JSESSIONID`;
- como roles e authorities controlam acesso;
- por que autenticacao por cookie exige protecao CSRF;
- como CORS controla chamadas feitas por outra origem no navegador;
- por que `401` e `403` representam situacoes diferentes;
- como logout e protecao contra session fixation encerram e fortalecem o ciclo da sessao;
- como esta estrategia deve conviver com JWT, OAuth2/OIDC e SAML no mesmo laboratorio.

## 1. Modelo mental

Autenticacao por sessao e um modelo stateful. Isso significa que o servidor mantem estado sobre o cliente autenticado.

Depois do login, o servidor armazena o `SecurityContext` dentro de uma `HttpSession`. O navegador recebe apenas um identificador opaco dessa sessao:

```text
JSESSIONID=valor_aleatorio
```

O cookie nao deve conter senha, role ou dados pessoais. Ele funciona como uma chave que permite ao servidor localizar o estado da sessao.

O fluxo geral e:

```text
cliente envia email e senha
        |
servidor valida as credenciais
        |
servidor cria uma Authentication autenticada
        |
Authentication entra no SecurityContext
        |
SecurityContext e salvo na HttpSession
        |
cliente recebe JSESSIONID
        |
proximas requisicoes enviam JSESSIONID
        |
servidor recupera SecurityContext da sessao
```

### Autenticacao, autorizacao e sessao

Os tres conceitos nao sao equivalentes:

```text
autenticacao -> comprova quem e o usuario
autorizacao  -> decide o que esse usuario pode fazer
sessao       -> mantem a autenticacao entre requisicoes
```

Um usuario pode estar autenticado e ainda assim nao estar autorizado a acessar `/usuarios/**`.

## 2. Contrato HTTP atual

### Registro

```text
POST /auth/session/register
```

Cria uma conta com role `USER`. O cliente nao pode escolher a propria role.

O registro e publico no sentido de autorizacao, mas continua sujeito a CSRF porque altera estado no servidor.

### Login

```text
POST /auth/session/login
```

Valida email e senha. Quando as credenciais sao validas, cria ou atualiza a sessao autenticada e devolve `JSESSIONID`.

O login tambem esta sujeito a CSRF. Proteger o login evita ataques em que uma vitima e autenticada involuntariamente na conta controlada por outra pessoa.

### Dados da autenticacao

```text
GET /auth/session/sobre
```

Retorna os dados selecionados do usuario e suas roles para a pagina do frontend
dedicada ao login por sessao.

### Logout

```text
POST /auth/session/logout
```

Exige autenticacao e token CSRF. Remove somente o contexto do login por sessao,
sem encerrar os contextos OAuth2 ou SAML mantidos no mesmo navegador.

### Emissao de token CSRF

```text
GET /auth/session/csrf
```

E publico para que o cliente obtenha um token antes do registro ou login. O parametro `CsrfToken` faz o Spring resolver o token adiado, grava-lo no cookie `XSRF-TOKEN` e disponibiliza-lo na resposta.

### Administracao de usuarios

```text
/usuarios/**
```

Todas as operacoes exigem `ROLE_ADMIN`.

## Mapa dos componentes

| Componente | Responsabilidade de seguranca |
|---|---|
| `SecurityConfigBySession` | Monta a chain, delimita paths e conecta autenticacao, autorizacao, CSRF, CORS e handlers |
| `SecurityFilterChain` | Define os filtros e regras aplicados a cada requisicao correspondente |
| `AuthenticationManager` | Coordena a validacao das credenciais |
| `UsernamePasswordAuthenticationToken` | Representa a tentativa de login e, depois da validacao, a autenticacao resultante |
| `UsuarioDetailsService` | Localiza a conta pelo email |
| `UsuarioDetails` | Adapta a entidade para username, password hash e authorities |
| `PasswordEncoder` | Codifica e verifica senha sem descriptografia |
| `SecurityContext` | Armazena a `Authentication` atual |
| `SecurityContextHolder` | Disponibiliza o contexto durante a requisicao |
| `HttpSessionSecurityContextRepository` | Persiste e recupera o contexto pela `HttpSession` |
| `SessionAuthenticationStrategy` | Executa protecoes associadas ao momento do login |
| `ChangeSessionIdAuthenticationStrategy` | Troca o identificador da sessao para impedir fixation |
| `CookieCsrfTokenRepository` | Persiste o token CSRF em cookie |
| `CsrfTokenRequestAttributeHandler` | Resolve o token recebido para validacao do `CsrfFilter` |
| `SessionCorsConfig` | Define origens, metodos, headers e credenciais aceitos pelo navegador |
| `SessionAuthenticationEntryPoint` | Responde `401` quando nao existe autenticacao valida |
| `SessionAccessDeniedHandler` | Responde `403` quando falta permissao |
| `GlobalExceptionHandler` | Converte falhas da camada MVC, incluindo credenciais invalidas no login customizado |
| `ObjectMapper` | Serializa `ProblemDetail` nos handlers executados pelos filtros |

## 3. Fluxo completo do registro

```text
JSON de registro
        |
RegisterRequestDTO valida formato e campos obrigatorios
        |
controller cria a entidade com Role.USER
        |
UsuarioService verifica se o email ja existe
        |
PasswordEncoder gera hash BCrypt
        |
repository persiste apenas o hash
```

### Por que usar um DTO de registro

O DTO define o contrato de entrada e impede que o cliente envie diretamente toda a entidade.

Isso e importante porque a entidade possui dados controlados pelo servidor, principalmente:

- identificador;
- hash da senha;
- role.

O cliente fornece a senha em texto puro apenas durante a requisicao protegida por HTTPS. A senha nao deve ser armazenada, registrada em log ou retornada na resposta.

### Por que a role nasce como USER

Aceitar a role enviada no body permitiria que uma pessoa se registrasse como `ADMIN`.

Por isso o backend ignora qualquer tentativa de escolha de privilegio e define:

```text
novo cadastro -> Role.USER
```

Promocao para administrador precisa ocorrer por um fluxo administrativo protegido, migration controlada ou operacao direta de laboratorio.

### Por que o hash pertence ao service

O `UsuarioService` centraliza a regra de persistencia da senha. Assim, qualquer fluxo que crie usuario passa pela mesma codificacao.

Codificar no controller e novamente no service produziria hash duplo. BCrypt nao compara uma senha pura com um hash que representa outro hash, portanto o login falharia.

## 4. Armazenamento seguro de senha

### PasswordEncoder

`PasswordEncoder` e a abstracao usada pelo Spring Security para codificar e verificar senhas.

O controller nao compara strings e nao chama uma funcao de descriptografia. A autenticacao delega a comparacao ao encoder.

### BCryptPasswordEncoder

O laboratorio usa `BCryptPasswordEncoder`.

BCrypt e adequado para senha porque:

- e uma funcao unidirecional;
- utiliza salt;
- hashes da mesma senha podem ser diferentes;
- possui custo adaptativo;
- torna tentativas massivas mais caras.

O fluxo correto e:

```text
cadastro:
senha pura -> encode -> hash persistido

login:
senha pura + hash persistido -> matches -> verdadeiro ou falso
```

Nao se deve:

- descriptografar senha;
- comparar senha pura com hash usando `equals`;
- gerar um novo hash e compara-lo por igualdade;
- salvar senha pura;
- escrever senha ou hash em logs.

## 5. Componentes da autenticacao

### UsernamePasswordAuthenticationToken

Antes da autenticacao, representa uma tentativa de login contendo principal e credencial:

```text
principal  -> email
credential -> senha informada
```

Nesse momento, o token ainda nao representa uma identidade confiavel.

Depois que o `AuthenticationManager` valida as credenciais, o resultado e uma `Authentication` autenticada contendo principal e authorities.

Usar esse objeto padrao permite participar da arquitetura do Spring Security em vez de criar uma verificacao paralela.

### AuthenticationManager

`AuthenticationManager` coordena o processo de autenticacao.

Ele nao deve conhecer detalhes HTTP nem construir respostas. Sua responsabilidade e receber uma tentativa de autenticacao e delegar para o mecanismo capaz de valida-la.

No fluxo de usuario e senha, o Spring utiliza os componentes disponiveis, principalmente:

- `UserDetailsService`;
- `PasswordEncoder`;
- provider de autenticacao por usuario e senha.

`AuthenticationConfiguration` disponibiliza o `AuthenticationManager` que o Spring montou a partir desses componentes. Isso evita instanciar manualmente um manager desconectado dos providers, do `UserDetailsService` e do `PasswordEncoder` registrados na aplicacao.

Essa delegacao separa:

```text
controller          -> transporte HTTP
AuthenticationManager -> coordenacao da autenticacao
UserDetailsService  -> carregamento da identidade
PasswordEncoder     -> verificacao da senha
```

### UsuarioDetailsService

`UsuarioDetailsService` implementa o contrato `UserDetailsService`.

Sua responsabilidade e localizar a conta pelo identificador usado no login. Neste projeto, o username do Spring corresponde ao email.

Se a conta nao existe, e lancada `UsernameNotFoundException`.

Por seguranca, a resposta externa nao deve revelar se o email existe. Falha por usuario inexistente e falha por senha incorreta devem resultar em uma mensagem generica:

```text
Credenciais invalidas
```

Isso reduz enumeracao de usuarios.

### UsuarioDetails

`UsuarioDetails` adapta a entidade da aplicacao ao modelo esperado pelo Spring Security.

O mapeamento atual e:

```text
Entidade.email    -> username
Entidade.password -> password hash
Entidade.role     -> authorities
```

Essa classe nao autentica sozinha. Ela apenas apresenta os dados da conta no formato que o provider consegue processar.

Os indicadores de conta expirada, bloqueada, credencial expirada e conta habilitada usam atualmente os valores padrao. Isso significa que o dominio ainda nao modela bloqueio, expiracao ou desativacao de conta.

### Authentication

`Authentication` representa a identidade de seguranca reconhecida pelo Spring.

Depois do login ela contem, conceitualmente:

- principal;
- estado autenticado;
- authorities;
- detalhes associados a autenticacao.

Ela nao deve ser confundida com a entidade JPA. A entidade representa dados de negocio; `Authentication` representa a identidade usada para decisoes de seguranca.

## 6. SecurityContext e persistencia

### SecurityContext

`SecurityContext` e o recipiente da `Authentication` atual.

Criar um contexto vazio antes de adicionar a nova autenticacao evita reaproveitar acidentalmente um contexto anterior.

### SecurityContextHolder

`SecurityContextHolder` disponibiliza o contexto durante a requisicao em processamento.

Controllers, filtros e mecanismos de autorizacao consultam esse holder para descobrir quem esta autenticado.

O holder sozinho nao persiste a autenticacao entre requisicoes. Sem um repositorio, o contexto desapareceria ao final da requisicao.

### SecurityContextRepository

`SecurityContextRepository` define como o contexto e carregado e salvo.

O laboratorio utiliza:

```text
HttpSessionSecurityContextRepository
```

Esse repositorio grava o contexto na `HttpSession`. Em uma requisicao futura, o filtro de contexto usa o `JSESSIONID` para localizar a sessao e restaurar a autenticacao.

O fluxo e:

```text
request com JSESSIONID
        |
container localiza HttpSession
        |
repository recupera SecurityContext
        |
SecurityContextHolder recebe o contexto
        |
autorizacao avalia as authorities
```

### HttpSession e JSESSIONID

`HttpSession` permanece no servidor. `JSESSIONID` permanece no cliente.

Possuir o cookie valido equivale a possuir a sessao. Por isso ele precisa ser protegido contra:

- vazamento;
- roubo por XSS;
- envio por conexao sem TLS;
- fixation;
- reutilizacao depois do logout;
- duracao excessiva.

O valor do cookie deve ser imprevisivel e gerenciado pelo container Servlet.

## 7. SecurityFilterChain

`SecurityFilterChain` define quais filtros e regras de seguranca processam uma requisicao.

### securityMatcher

O escopo atual inclui:

```text
/auth/session/**
/usuarios/**
```

`securityMatcher` decide se a chain inteira se aplica a uma requisicao.

Isso e diferente de `requestMatchers`, que define regras de autorizacao dentro de uma chain que ja foi selecionada.

Uma regra interna para `/usuarios/**` nao teria efeito se `/usuarios/**` estivesse fora do `securityMatcher`.

### Order

Quando existirem varias chains, a ordem define qual sera avaliada primeiro.

Somente a primeira chain correspondente processa a requisicao. Por isso duas chains genericas ou sobrepostas podem produzir comportamento incorreto.

As futuras estrategias devem ter escopos claros, por exemplo:

```text
/auth/session/**
/auth/token/**
/auth/oauth2/**
/auth/saml/**
```

Recursos compartilhados, como `/usuarios/**`, exigem uma decisao arquitetural explicita sobre quais mecanismos podem autenticar o acesso. Nao se deve colocar o mesmo path em varias chains esperando que todas sejam executadas.

### authorizeHttpRequests

As regras atuais separam:

```text
registro e login -> permitAll
logout           -> authenticated
/usuarios/**     -> hasRole("ADMIN")
demais paths da chain -> authenticated
```

`permitAll` remove a exigencia de autenticacao ou role para aquela rota. Ele nao desativa CSRF, CORS nem outras protecoes da chain.

## 8. Roles e authorities

Role e uma categoria de acesso da aplicacao. Authority e a representacao concreta usada pelo Spring Security.

O projeto converte:

```text
USER  -> ROLE_USER
ADMIN -> ROLE_ADMIN
```

`hasRole("ADMIN")` procura internamente a authority `ROLE_ADMIN`.

Se o prefixo for montado incorretamente, o usuario pode estar autenticado e ainda receber `403`.

As authorities sao carregadas no login e ficam dentro da `Authentication` salva na sessao. Se a role for alterada no banco, a sessao atual nao e atualizada automaticamente. O usuario precisa autenticar novamente ou ter sua sessao invalidada por um mecanismo administrativo.

## 9. Respostas 401 e 403

### 401 Unauthorized

Significa que a requisicao nao possui autenticacao valida.

Exemplos:

- acesso a `/usuarios/**` sem sessao;
- `JSESSIONID` ausente, invalido ou expirado;
- email ou senha invalidos no login.

### 403 Forbidden

Significa que a identidade foi reconhecida, mas nao tem permissao suficiente, ou que uma protecao de seguranca rejeitou a requisicao.

Exemplos:

- usuario `USER` tentando acessar `/usuarios/**`;
- token CSRF ausente ou incorreto;
- preflight CORS de origem nao permitida.

Nao se deve retornar `404` para credenciais invalidas, porque isso revelaria informacao sobre a existencia da conta.

## 10. Handlers de seguranca

### SessionAuthenticationEntryPoint

`SessionAuthenticationEntryPoint` produz a resposta quando uma requisicao tenta acessar um recurso protegido sem autenticacao.

Ele retorna `401` no formato `application/problem+json`.

Esse componente pertence ao Spring Security porque a rejeicao acontece na cadeia de filtros, antes de um controller processar a requisicao.

### SessionAccessDeniedHandler

`SessionAccessDeniedHandler` produz a resposta quando existe autenticacao, mas falta autorizacao.

Ele retorna `403` no mesmo formato padronizado.

Separar os dois handlers preserva a semantica:

```text
nao autenticado        -> AuthenticationEntryPoint -> 401
autenticado sem acesso -> AccessDeniedHandler      -> 403
```

### Por que o GlobalExceptionHandler nao substitui esses handlers

`@RestControllerAdvice` trata excecoes resolvidas pela camada MVC.

Rejeicoes do `AuthorizationFilter`, `ExceptionTranslationFilter` e outros filtros acontecem antes do controller. Por isso precisam dos contratos do Spring Security.

Existe uma excecao importante no login customizado: `AuthenticationManager` e chamado dentro do controller. Uma `AuthenticationException` lancada nesse ponto chega a camada MVC e e convertida pelo `GlobalExceptionHandler` em `401`.

### ProblemDetail

`ProblemDetail` padroniza respostas de erro com campos como:

```text
title
status
detail
instance
```

`ObjectMapper` transforma o objeto em JSON no response dos handlers de filtro.

O formato consistente ajuda clientes a tratar erros sem depender de mensagens livres.

## 11. CSRF

### O que e

CSRF significa Cross-Site Request Forgery.

O risco existe porque navegadores enviam cookies automaticamente para o dominio correspondente. Um site malicioso pode tentar fazer o navegador da vitima enviar uma operacao para a aplicacao enquanto a vitima possui uma sessao valida.

```text
vitima faz login
        |
navegador guarda JSESSIONID
        |
vitima visita site malicioso
        |
site malicioso tenta enviar POST para a API
        |
navegador pode anexar JSESSIONID automaticamente
```

Sem outra verificacao, o servidor poderia interpretar a operacao como legitima.

### Por que o token CSRF protege

O servidor exige uma segunda prova alem do cookie de sessao.

O navegador pode anexar cookies automaticamente, mas um site atacante nao deve conseguir ler o token e coloca-lo corretamente no header por causa da politica de mesma origem.

Neste projeto:

```text
cookie -> XSRF-TOKEN
header -> X-XSRF-TOKEN
```

O servidor compara os valores. Se estiverem ausentes ou diferentes, a requisicao e rejeitada.

### CookieCsrfTokenRepository

`CookieCsrfTokenRepository` persiste o token CSRF em cookie.

Foi usado `withHttpOnlyFalse()` para permitir que uma futura aplicacao web leia `XSRF-TOKEN` e copie o valor para o header.

Essa escolha e comum em arquiteturas SPA, mas tem uma consequencia: JavaScript executado na origem consegue ler esse cookie. Por isso protecao contra XSS continua essencial. Se um invasor executa JavaScript dentro da aplicacao, ele pode ler o token e contornar CSRF.

O token CSRF:

- nao autentica o usuario;
- nao substitui `JSESSIONID`;
- nao contem role;
- nao deve ser usado como access token.

### CsrfTokenRequestAttributeHandler

`CsrfTokenRequestAttributeHandler` resolve o token recebido na requisicao e o disponibiliza para o `CsrfFilter`.

Com essa configuracao, o cliente envia diretamente no header o valor observado no cookie.

Nao e necessario criar filtro customizado para ler `X-XSRF-TOKEN`. O `CsrfFilter` e o repository ja implementam esse fluxo.

### Rotacao depois do login

`CsrfAuthenticationStrategy` participa da estrategia composta executada depois que as credenciais sao validadas. Ela remove o token utilizado antes do login, fazendo com que o cliente obtenha outro em `GET /auth/session/csrf`.

O fluxo evita continuar usando indefinidamente o mesmo token atraves da mudanca de estado entre usuario anonimo e autenticado:

```text
GET /auth/session/csrf -> token anonimo
POST /auth/session/login -> token anonimo e invalidado
GET /auth/session/csrf -> token autenticado novo
```

Credenciais invalidas nao executam a estrategia e, portanto, nao rotacionam a sessao nem o token.

### Metodos protegidos

Requisicoes que alteram estado precisam de CSRF:

```text
POST
PUT
PATCH
DELETE
```

Requisicoes de leitura, como `GET`, nao devem alterar estado e normalmente nao exigem o token.

### permitAll nao desativa CSRF

Registro e login sao publicos para autorizacao, mas continuam sendo `POST`.

Logo:

```text
permitAll + POST + CSRF ativo -> token CSRF continua obrigatorio
```

### Erro observado no laboratorio

O header correto e:

```text
X-XSRF-TOKEN
```

O header incorreto usado durante o estudo foi:

```text
X-SRF-TOKEN
```

Quando o nome estava errado, o log mostrou:

```text
Invalid CSRF token found
```

Isso confirmou que o filtro estava ativo e que o problema era o contrato HTTP, nao ausencia de um filtro customizado.

### CSRF e XSS

CSRF protege contra requisicoes forjadas de outra origem. Ele nao impede JavaScript malicioso executado dentro da propria origem.

Por isso CSRF deve ser combinado com:

- tratamento seguro de entrada e saida;
- Content Security Policy quando houver frontend;
- ausencia de scripts injetados;
- dependencias atualizadas;
- cookies configurados corretamente.

## 12. CORS

### O que e uma origem

Uma origem e composta por:

```text
esquema + host + porta
```

Exemplos diferentes:

```text
http://localhost:5173
http://localhost:8080
https://localhost:5173
```

Mesmo host com porta diferente e outra origem.

### O que CORS faz

CORS define quais origens podem ler e realizar requisicoes para a API a partir de um navegador.

Ele nao e um mecanismo de autenticacao e nao protege a API contra clientes como:

- Insomnia;
- curl;
- Postman;
- outro backend.

Esses clientes nao aplicam a politica CORS do navegador.

### Por que CORS deve ser processado antes da seguranca

Requisicoes cross-origin nao simples geram um preflight `OPTIONS`.

O preflight pergunta ao servidor se a origem, o metodo e os headers sao permitidos. Ele normalmente nao contem `JSESSIONID`.

Se a autorizacao for executada antes de CORS, o preflight pode ser rejeitado como nao autenticado. Por isso o Spring integra o processamento CORS antes da verificacao de acesso.

### SessionCorsConfig

`SessionCorsConfig` cria um `CorsConfigurationSource` especifico para a estrategia de sessao.

O escopo atual e:

```text
/auth/session/**
/usuarios/**
```

A origem de desenvolvimento e externalizada:

```properties
app.security.session.allowed-origin=${SESSION_FRONTEND_ORIGIN:http://localhost:5173}
```

Externalizar evita fixar configuracao de ambiente no codigo.

O source possui um nome proprio, `sessionCorsConfigurationSource`. O `@Qualifier` usado na chain seleciona explicitamente esse source. Essa identificacao evita ambiguidade quando o laboratorio possuir outra configuracao CORS para token, OAuth2 ou SAML.

### Origens permitidas

A origem precisa ser explicita:

```text
http://localhost:5173
```

Nao se deve combinar:

```text
allowedOrigins("*")
allowCredentials(true)
```

Permitir credenciais para qualquer origem seria inseguro e essa combinacao e rejeitada pelas regras CORS.

### Metodos permitidos

```text
GET
POST
PATCH
DELETE
OPTIONS
```

Permitir apenas os metodos usados reduz a superficie exposta pelo navegador.

### Headers permitidos

```text
Content-Type
X-XSRF-TOKEN
```

`Content-Type` permite JSON. `X-XSRF-TOKEN` permite enviar a prova CSRF.

### allowCredentials

`allowCredentials(true)` autoriza o navegador a enviar cookies em chamadas cross-origin.

Isso e necessario para:

- `JSESSIONID`;
- `XSRF-TOKEN`.

O frontend tambem precisa solicitar credenciais:

```javascript
fetch(url, {
  credentials: "include"
});
```

Configurar apenas o backend nao faz o navegador anexar cookies automaticamente em toda chamada cross-origin.

### maxAge

`maxAge(3600)` permite que o navegador armazene o resultado positivo do preflight por uma hora.

Isso reduz requisicoes `OPTIONS`, mas alteracoes na politica CORS podem levar ate o fim desse cache para serem percebidas pelo navegador.

### CORS nao substitui CSRF

As duas protecoes devem coexistir:

```text
CORS -> controla origens permitidas no navegador
CSRF -> exige prova adicional em operacoes autenticadas por cookie
```

CORS sozinho nao e defesa suficiente contra todos os cenarios CSRF e nao deve ser usado como substituto do token.

## 13. Login por sessao

O fluxo seguro implementado e:

```text
1. validar o body
2. criar tentativa UsernamePasswordAuthenticationToken
3. AuthenticationManager valida credenciais
4. SessionAuthenticationStrategy protege a sessao
5. criar SecurityContext vazio
6. adicionar Authentication autenticada
7. publicar o contexto no SecurityContextHolder
8. salvar o contexto no SecurityContextRepository
9. devolver sucesso ao cliente
```

### Por que salvar explicitamente o contexto

O login foi implementado em controller e nao pelo filtro padrao de login.

Por isso o fluxo precisa salvar explicitamente o contexto. Apenas colocar a `Authentication` no holder nao garante persistencia para a proxima requisicao.

### Credenciais invalidas

O erro externo e sempre generico:

```text
401 Unauthorized
Credenciais invalidas
```

Isso vale tanto para email inexistente quanto para senha incorreta.

### Validacao do body

Email malformado, email vazio ou senha vazia resultam em `400 Bad Request`.

Essa validacao ocorre antes da tentativa de autenticacao e evita enviar dados estruturalmente invalidos ao mecanismo de seguranca.

## 14. Protecao contra session fixation

### O ataque

Session fixation ocorre quando um atacante conhece ou prepara um identificador de sessao e tenta fazer a vitima autenticar usando esse mesmo identificador.

```text
atacante conhece uma sessao
        |
vitima utiliza essa sessao
        |
vitima faz login
        |
identificador nao muda
        |
atacante reutiliza o identificador autenticado
```

### ChangeSessionIdAuthenticationStrategy

`ChangeSessionIdAuthenticationStrategy` troca o identificador da sessao depois que as credenciais foram validadas.

Em containers Servlet modernos, utiliza o suporte de `changeSessionId`.

O objetivo e:

```text
JSESSIONID anterior -> deixa de identificar a sessao autenticada
JSESSIONID novo     -> representa a sessao depois do login
```

Os atributos necessarios da sessao permanecem disponiveis, mas o identificador conhecido antes do login perde valor.

### Por que a estrategia precisa ser chamada no login customizado

Nas versoes atuais do Spring Security, cada mecanismo de autenticacao deve notificar sua `SessionAuthenticationStrategy`.

Mecanismos fornecidos pelo framework fazem isso internamente. Como este laboratorio chama `AuthenticationManager` dentro de um controller, o proprio fluxo precisa executar a estrategia.

Configurar apenas o DSL de session management nao substitui essa notificacao no mecanismo customizado.

### Ordem correta

```text
validar credenciais
        |
executar SessionAuthenticationStrategy
        |
trocar o identificador da sessao
        |
montar SecurityContext
        |
salvar contexto na sessao protegida
```

A estrategia deve ser um componente configurado e injetado no mecanismo de login. Cria-la diretamente a cada chamada mistura configuracao com controle HTTP e dificulta evoluir para estrategias compostas, como controle de sessoes concorrentes.

## 15. Logout

O logout precisa encerrar o estado no servidor.

O fluxo atual:

```text
remover XSRF-TOKEN
        |
limpar SecurityContextHolder
        |
salvar um contexto vazio no repositorio da modalidade
```

### Por que limpar o SecurityContextHolder

Remove a autenticacao da requisicao atual.

### Por que remover o contexto persistido

O repositorio usa a chave `SPRING_SECURITY_CONTEXT_SESSION`. Salvar um contexto
vazio remove somente essa autenticacao da `HttpSession`. Os contextos OAuth2 e
SAML usam chaves diferentes e permanecem independentes.

Limpar somente o holder nao seria suficiente, porque a proxima requisicao poderia restaurar o contexto salvo na sessao.

### CSRF no logout

Logout altera estado e usa cookie. Sem CSRF, outro site poderia provocar logout involuntario da vitima.

O `CsrfTokenRepository` tambem recebe um token nulo no logout. Para o repository baseado em cookie, isso produz um `XSRF-TOKEN` expirado. Um novo registro ou login precisa comecar por `GET /auth/session/csrf`.

## 16. Fluxo de uma futura SPA

Uma aplicacao web em `http://localhost:5173` seguira, conceitualmente:

```text
1. navegador solicita ou recebe XSRF-TOKEN
2. JavaScript le XSRF-TOKEN
3. JavaScript envia POST de login
4. request inclui credentials: "include"
5. request inclui X-XSRF-TOKEN
6. CORS valida a origem e o preflight
7. CSRF valida cookie e header
8. credenciais sao autenticadas
9. identificador de sessao e protegido contra fixation
10. SecurityContext e salvo
11. navegador guarda JSESSIONID
12. chamadas futuras enviam JSESSIONID
```

Em operacoes mutaveis, a SPA continua enviando `X-XSRF-TOKEN`.

## 17. Matriz de resultados esperados

| Cenario | Resultado |
|---|---|
| Body de login invalido | `400 Bad Request` |
| Email ou senha incorretos | `401 Unauthorized` |
| Acesso protegido sem sessao | `401 Unauthorized` |
| Usuario `USER` acessando `/usuarios/**` | `403 Forbidden` |
| Usuario `ADMIN` acessando `/usuarios/**` | acesso permitido |
| Token CSRF ausente ou incorreto | `403 Forbidden` |
| Origem CORS nao permitida | preflight rejeitado |
| Email duplicado no registro | `409 Conflict` |
| Erro nao mapeado | `500 Internal Server Error` |

## 18. Relacao entre cookies

### JSESSIONID

```text
finalidade -> identificar a HttpSession
sensibilidade -> alta
autentica indiretamente -> sim
deve ser legivel por JavaScript -> nao
```

### XSRF-TOKEN

```text
finalidade -> fornecer a prova CSRF
sensibilidade -> nao substitui credencial, mas deve ser protegido
autentica usuario -> nao
legivel por JavaScript neste projeto -> sim
```

Os dois cookies participam do mesmo fluxo, mas possuem responsabilidades diferentes.

## 19. Limites e endurecimentos ainda necessarios

Esta etapa implementa a base, mas uma aplicacao de producao ainda deve avaliar:

### HTTPS

Credenciais e cookies devem trafegar por TLS. CORS, CSRF e BCrypt nao protegem dados enviados em HTTP contra interceptacao de rede.

### Cookie Secure

Em producao, `JSESSIONID` deve usar `Secure` para ser enviado apenas por HTTPS.

### Cookie HttpOnly

`JSESSIONID` deve ser `HttpOnly` para reduzir roubo direto por JavaScript. Isso nao elimina todo impacto de XSS, mas impede leitura simples do cookie.

`XSRF-TOKEN` permanece legivel por JavaScript devido ao modelo SPA adotado.

### SameSite

`SameSite` reduz envio de cookie em determinados contextos cross-site e funciona como defesa adicional. Ele nao substitui CSRF.

A escolha entre `Lax`, `Strict` e `None` depende da navegacao e das origens reais. `None` exige `Secure`.

### Timeout

Sessoes precisam expirar. O tempo deve equilibrar risco e experiencia do usuario.

Contas administrativas normalmente justificam politicas mais restritivas.

### Sessoes concorrentes

Ainda nao foi definido se uma conta pode manter varias sessoes simultaneas.

Uma politica futura pode:

- limitar quantidade de sessoes;
- rejeitar novo login;
- expirar uma sessao antiga;
- permitir revogacao administrativa.

### Rotacao do token CSRF

A rotacao depois do login e a remocao no logout estao implementadas. A futura interface ainda deve ser testada para garantir que sempre obtenha um token novo antes da proxima operacao mutavel.

### Persistencia distribuida

`HttpSession` em memoria pertence a uma instancia da aplicacao.

Com varias instancias, sera necessario:

- sticky session no balanceador; ou
- armazenamento compartilhado, como Spring Session com Redis.

Sem isso, uma requisicao enviada a outra instancia pode nao encontrar a sessao.

### Auditoria e rate limiting

Uma aplicacao real deve considerar:

- limite de tentativas de login;
- atraso progressivo;
- bloqueio controlado;
- auditoria de sucesso e falha sem registrar senha;
- alertas para comportamento suspeito.

### XSS

Como JavaScript pode ler `XSRF-TOKEN`, uma vulnerabilidade XSS compromete a protecao CSRF. O frontend precisa de defesa propria contra injecao de script.

## 20. Multiplas formas de autenticacao

O cadastro cria uma identidade unica. Essa identidade pode ser autenticada futuramente por mecanismos diferentes.

```text
registro da conta
        |
login por sessao -> JSESSIONID
login por token  -> access token
login OAuth2     -> fluxo OIDC/OAuth2
login SAML       -> assertion e sessao correspondente
```

Nao e necessario criar uma conta duplicada para cada mecanismo.

Cada estrategia pode ter:

- sua propria `SecurityFilterChain`;
- seu proprio escopo de paths;
- handlers adequados ao protocolo;
- regras de persistencia diferentes;
- politica CORS especifica.

Exemplos:

```text
sessao -> stateful, cookie, CSRF necessario
Bearer -> stateless, Authorization header, regras RFC 6750
OIDC   -> redirect, state, nonce, authorization code
SAML   -> metadata, assertion, ACS, assinatura XML
```

Um handler generico de sessao nao deve substituir automaticamente handlers de Bearer Token, porque Bearer possui requisitos proprios, como `WWW-Authenticate`.

## 21. Testes existentes

Os testes atuais cobrem:

- criacao da tentativa de login;
- persistencia do `SecurityContext`;
- registro sempre como `USER`;
- logout com e sem sessao;
- emissao do token CSRF pelo endpoint;
- rotacao CSRF depois do login;
- expiracao do cookie CSRF no logout;
- validacao do body de login;
- credenciais invalidas retornando `401`;
- `ProblemDetail` de `401` e `403`;
- preflight CORS para os paths de sessao e usuarios;
- rejeicao de origem CORS desconhecida;
- troca real do identificador da sessao no login;
- chain real retornando `401` para anonimo e `403` para usuario sem role;
- acesso de `ADMIN` as rotas `/usuarios/**`;
- exigencia de CSRF mesmo em um `POST` marcado com `permitAll`;
- fluxo integrado entre emissao do CSRF e login.

Esses testes nao substituem testes de integracao com navegador real, container de banco e ambiente HTTPS.

Ainda devem ser adicionados testes de ambiente para:

- expiracao de sessao;
- atributos finais dos cookies sob HTTPS;
- politica de sessoes concorrentes.

## 22. Checklist de seguranca da etapa

- [x] Senha armazenada como hash BCrypt.
- [x] Comparacao de senha delegada ao `PasswordEncoder`.
- [x] Cliente nao escolhe role no registro.
- [x] Registro e login separados.
- [x] `SecurityContext` salvo em `HttpSession`.
- [x] Rotas administrativas protegidas por role.
- [x] CSRF ativo em operacoes baseadas em cookie.
- [x] Header CSRF correto documentado.
- [x] CORS com origem explicita.
- [x] Credenciais habilitadas apenas para origem conhecida.
- [x] `401` e `403` tratados separadamente.
- [x] Erros de seguranca retornados como `ProblemDetail`.
- [x] Logout remove somente o contexto da modalidade.
- [x] Protecao contra session fixation considerada no login customizado.
- [ ] Politica explicita de cookie para producao.
- [ ] Timeout revisado.
- [ ] Controle de sessoes concorrentes.
- [x] Rotacao CSRF depois do login.
- [x] Remocao do token CSRF no logout.
- [ ] Testes HTTPS e de navegador.
- [ ] Persistencia distribuida de sessao, se houver escala horizontal.

## 23. Referencias

- [Spring Security - Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [Spring Security - Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [Spring Security - Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Spring Security - CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security - CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [Spring Security - Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
