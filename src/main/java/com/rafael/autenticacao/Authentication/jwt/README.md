# Autenticacao e autorizacao com JWT

Este documento registra a implementacao de autenticacao baseada em access token
JWT deste laboratorio. Ele explica os conceitos, os fluxos, as responsabilidades
das classes, as decisoes de seguranca e os limites da solucao atual.

O mecanismo implementado nao usa apenas JWT. A arquitetura e hibrida:

```text
access token  -> JWT assinado, curto e stateless
refresh token -> valor opaco, longo e controlado pelo servidor
```

Essa distincao e essencial. O JWT autentica chamadas da API durante um periodo
curto. O refresh token permite renovar essa autenticacao e fornece ao servidor
controle sobre rotacao, reutilizacao, revogacao e logout.

## 1. O que e JWT

JWT significa JSON Web Token. Ele e um formato compacto para transportar claims
entre partes. JWT nao e, sozinho, um protocolo de login e nao define como email e
senha devem ser validados.

Neste laboratorio:

1. email e senha sao validados pelo Spring Security;
2. depois da autenticacao, a aplicacao emite um JWT;
3. o cliente apresenta esse JWT nas chamadas protegidas;
4. a API valida assinatura, emissor, audiencia e tempo de expiracao;
5. as authorities do token participam das decisoes de autorizacao.

Um JWT compacto possui tres partes separadas por ponto:

```text
header.payload.signature
```

### Header

Informa metadados sobre o token. O access token atual usa:

```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

### Payload

Contem claims. Claims sao declaracoes sobre o token e sobre o principal. Elas
sao codificadas em Base64URL, mas nao ficam criptografadas.

Qualquer pessoa que possua o token consegue ler o header e o payload. Por isso,
nao se deve colocar senha, segredo, documento pessoal ou dado sensivel no JWT.

### Signature

A assinatura permite detectar alteracoes no header ou no payload. A aplicacao
assina com a chave privada RSA e valida com a chave publica.

A assinatura oferece integridade e autenticidade. Ela nao oferece sigilo.

## 2. JWT assinado nao e JWT criptografado

O access token deste projeto e um JWS assinado com RS256. Ele nao e um JWE
criptografado.

```text
assinatura  -> impede alteracao sem deteccao
criptografia -> impede leitura por quem nao possui a chave
```

O cliente nao deve confiar apenas no conteudo decodificado. Somente depois da
validacao criptografica e das claims o token pode representar uma identidade
confiavel.

## 3. Modelo de seguranca adotado

O fluxo combina quatro credenciais diferentes:

| Credencial | Local | Duracao | Finalidade |
|---|---|---:|---|
| Senha | Enviada somente no login | Apenas a requisicao | Comprovar a identidade inicial |
| Access token JWT | Corpo da resposta e header `Authorization` | 15 minutos | Acessar recursos protegidos |
| Refresh token opaco | Cookie `HttpOnly` | 2 horas absolutas | Obter novo par de tokens |
| Token CSRF | Cookie legivel e header customizado | Gerenciado pelo Spring | Proteger operacoes baseadas em cookie |

O access token e stateless: sua validacao nao consulta a tabela de refresh
tokens. O refresh token e stateful: o servidor consulta seu hash no PostgreSQL.

Essa arquitetura evita manter uma sessao HTTP e ainda permite controlar a
renovacao de credenciais.

## 4. Contrato HTTP atual

Todas as rotas deste mecanismo estao sob:

```text
/auth/jwt/**
```

### Obter token CSRF

```http
GET /auth/jwt/csrf
```

Retorna os dados do token e cria o cookie:

```text
JWT-XSRF-TOKEN=<valor>
```

O cliente deve enviar o mesmo valor no header das operacoes protegidas:

```text
X-JWT-XSRF-TOKEN: <valor>
```

### Login

```http
POST /auth/jwt/login
Content-Type: application/json
X-JWT-XSRF-TOKEN: <valor>
```

```json
{
  "email": "rafael@email.com",
  "password": "123456"
}
```

Resposta:

```json
{
  "accessToken": "<jwt-assinado>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

A resposta tambem cria o cookie `refresh_token`.

### Recurso autenticado

```http
GET /auth/jwt/sobre
Authorization: Bearer <access-token>
```

O endpoint retorna `subject`, username, nome, email e as roles do JWT. O
`subject` continua sendo o UUID do usuario, nao seu email.

### Renovacao

```http
POST /auth/jwt/refresh
Cookie: refresh_token=<valor>; JWT-XSRF-TOKEN=<valor>
X-JWT-XSRF-TOKEN: <valor>
```

Nao exige access token. O refresh precisa funcionar quando o access token ja
expirou. Se o refresh token for valido, o servidor:

1. marca o token atual como usado;
2. gera outro refresh token da mesma familia;
3. preserva a expiracao absoluta da familia;
4. emite um novo access token;
5. substitui o cookie de refresh.

### Logout

```http
POST /auth/jwt/logout
Cookie: refresh_token=<valor>; JWT-XSRF-TOKEN=<valor>
X-JWT-XSRF-TOKEN: <valor>
```

Nao exige access token, mas exige CSRF. O logout:

1. revoga a familia encontrada pelo refresh token;
2. remove o cookie `refresh_token`;
3. remove o cookie `JWT-XSRF-TOKEN`;
4. retorna `204 No Content`.

O endpoint e idempotente: cookie ausente ou token desconhecido nao revela se
uma credencial existia.

## 5. Claims do access token

`JwtTokenService` emite as seguintes claims:

| Claim | Conteudo | Motivo |
|---|---|---|
| `iss` | `https://autenticacao-lab.local` | Identifica quem emitiu o token |
| `sub` | UUID do usuario | Identifica o principal de forma estavel |
| `aud` | `autenticacao-api` | Restringe o token a esta API |
| `email` | Email autenticado | Dado auxiliar para o consumidor |
| `iat` | Instante de emissao | Registra quando o token nasceu |
| `exp` | Emissao mais 15 minutos | Limita a janela de uso |
| `jti` | UUID aleatorio | Identifica unicamente a emissao |
| `authorities` | Roles/authorities ordenadas | Alimenta a autorizacao do Spring |

O `JwtDecoder` valida a assinatura RS256, o emissor, a audiencia e as validacoes
temporais padrao. Alterar o payload invalida a assinatura.

### Por que usar subject com UUID

Email pode ser alterado. Um UUID de usuario tende a ser um identificador interno
mais estavel. O email continua disponivel como claim auxiliar, mas nao e a
identidade primaria do token.

### Por que o access token dura 15 minutos

Um JWT emitido continua valido ate expirar, salvo se for adicionada uma lista de
revogacao ou outra verificacao stateful. Uma duracao curta reduz a janela de
abuso quando o token e roubado.

O tempo nao deve ser tao curto que torne a aplicacao instavel, nem tao longo que
transforme um vazamento em acesso prolongado. O valor adequado depende do risco
do sistema.

## 6. Assinatura assimetrica com RS256

O projeto usa um par de chaves RSA de 2048 bits:

```text
chave privada -> assina
chave publica -> valida
```

Essa separacao permite que um servico de autorizacao mantenha a chave privada e
que varias APIs validem tokens usando apenas a chave publica.

Em comparacao, um algoritmo simetrico como HS256 usa o mesmo segredo para
assinar e validar. Todo servico validador que recebe esse segredo tambem ganha a
capacidade de emitir tokens.

### Limitacao atual das chaves

`JwtKeyConfig` gera um novo par RSA durante cada inicializacao da aplicacao.
Consequentemente, todos os access tokens emitidos antes de um reinicio deixam de
ser validos.

Isso e aceitavel para o laboratorio, mas nao para uma implantacao distribuida.
Em producao, as chaves devem ser persistidas e protegidas em um secret manager,
keystore, HSM ou servico de gerenciamento de chaves. Tambem devem existir:

- identificador de chave (`kid`);
- politica de rotacao;
- periodo de sobreposicao entre chave nova e antiga;
- publicacao segura das chaves publicas, quando houver consumidores externos;
- plano de resposta para comprometimento da chave privada.

## 7. SecurityFilterChain do JWT

`SecurityConfigByJwt` declara uma chain com `@Order(2)` e:

```text
securityMatcher("/auth/jwt/**")
```

`securityMatcher` decide se a chain inteira processara a requisicao.
`requestMatchers` decide a autorizacao depois que a chain ja foi selecionada.

As regras atuais sao:

```text
POST /auth/jwt/login   -> permitAll
POST /auth/jwt/refresh -> permitAll
POST /auth/jwt/logout  -> permitAll
GET  /auth/jwt/csrf    -> permitAll
demais rotas           -> authenticated
```

`permitAll` remove a exigencia de autenticacao. Ele nao desativa CSRF, CORS nem
os demais filtros.

### Stateless

A chain usa:

```text
SessionCreationPolicy.STATELESS
```

O Spring nao deve usar `HttpSession` para persistir a identidade do JWT. Cada
requisicao protegida precisa carregar seu proprio access token.

Stateless nao significa ausencia total de estado no sistema. A tabela de refresh
tokens continua sendo estado de seguranca mantido pelo servidor.

### Resource Server

`oauth2ResourceServer().jwt()` instala o suporte para Bearer token. Em uma
requisicao protegida, o Spring:

1. extrai o token do header `Authorization`;
2. decodifica e valida o JWT;
3. converte as claims em uma `JwtAuthenticationToken`;
4. coloca a autenticacao no `SecurityContext` apenas durante a requisicao;
5. executa as regras de autorizacao.

Nao foi criado um filtro JWT manual. O suporte padrao do Spring Security ja
resolve extracao, validacao, falhas e integracao com o contexto.

## 8. Roles e authorities

O token carrega uma claim customizada chamada `authorities`:

```json
{
  "authorities": ["ROLE_USER"]
}
```

`JwtGrantedAuthoritiesConverter` foi configurado para:

- ler `authorities`, em vez da claim padrao de scopes;
- nao adicionar prefixo, pois os valores ja possuem `ROLE_`.

O mapeamento esperado e:

```text
USER  -> ROLE_USER
ADMIN -> ROLE_ADMIN
```

`hasRole("ADMIN")` procura internamente `ROLE_ADMIN`.

As authorities representam um retrato do momento da emissao. Se a role for
alterada no banco, access tokens existentes nao mudam. A alteracao aparecera em
uma nova emissao, e o token antigo podera continuar com os privilegios anteriores
ate expirar.

Para operacoes de alto risco, pode ser necessario consultar estado atual,
revogar credenciais ou exigir nova autenticacao.

## 9. Login e emissao

O login reutiliza os componentes compartilhados com a autenticacao por sessao:

```text
LoginRequestDTO
      |
UsernamePasswordAuthenticationToken.unauthenticated
      |
AuthenticationManager
      |
UsuarioDetailsService + PasswordEncoder
      |
Authentication autenticada
      |\
      | +-> JwtRefreshTokenService.issue
      |
      +----> JwtTokenService.issueToken
```

O controller nao compara senhas. Ele entrega a tentativa ao
`AuthenticationManager`, que usa os providers configurados pelo Spring.

Somente uma `Authentication` ja validada pode chegar aos servicos de emissao.
`JwtRefreshTokenService.issue` ainda confirma que o principal e um
`UsuarioDetails` autenticado.

## 10. Access token e refresh token nao sao equivalentes

### Access token

- e um JWT;
- vai no header `Authorization`;
- e enviado a cada recurso protegido;
- dura 15 minutos;
- contem claims e authorities;
- nao e persistido no banco;
- nao e consultado durante logout ou refresh.

### Refresh token

- e um valor opaco aleatorio, nao um JWT;
- vai em cookie `HttpOnly`;
- e usado apenas em refresh e logout;
- possui 256 bits aleatorios antes da codificacao;
- dura no maximo 2 horas nesta configuracao;
- tem somente seu hash SHA-256 persistido;
- e rotacionado a cada uso;
- pertence a uma familia revogavel.

Transformar o refresh token em JWT nao traria vantagem para este modelo. O
servidor precisa consultar estado para uso unico, revogacao e deteccao de
reutilizacao. Um valor opaco reduz dados expostos e simplifica esse controle.

## 11. Geracao e armazenamento do refresh token

`JwtRefreshTokenCodec` usa `SecureRandom` para gerar 32 bytes aleatorios e os
codifica com Base64URL sem padding.

Antes de persistir, calcula:

```text
SHA-256(refresh token) -> hexadecimal com 64 caracteres
```

O valor puro existe apenas no momento da emissao e no cookie do cliente. O banco
armazena `token_hash`.

### Por que SHA-256 e adequado aqui

Senhas possuem entropia humana baixa e precisam de uma funcao lenta, como
BCrypt. O refresh token e gerado aleatoriamente com alta entropia. Um hash
criptografico rapido e suficiente para impedir que uma leitura simples do banco
revele o token utilizavel.

Isso depende de o token continuar realmente aleatorio, longo e imprevisivel.
Nunca se deve aplicar a mesma justificativa a senhas escolhidas por pessoas.

## 12. Persistencia do refresh token

A migration cria `jwt_refresh_token` com:

| Coluna | Funcao |
|---|---|
| `id` | Identidade do registro |
| `usuario_id` | Dono da credencial |
| `family_id` | Agrupa todas as rotacoes iniciadas pelo mesmo login |
| `token_hash` | Hash unico usado para localizar o token |
| `created_at` | Momento de criacao deste membro |
| `expires_at` | Limite absoluto da familia |
| `used_at` | Marca que o token ja foi consumido |
| `revoked_at` | Marca revogacao explicita ou por incidente |

Ha indices para usuario, familia e expiracao. A constraint de tamanho garante
64 caracteres para o hash SHA-256 hexadecimal.

## 13. Rotacao do refresh token

Refresh token rotation significa substituir o token depois de cada uso.

```text
R1 ativo
   |
   | refresh valido
   v
R1 usado + R2 ativo
              |
              | refresh valido
              v
          R2 usado + R3 ativo
```

Todos pertencem ao mesmo `family_id`.

O fluxo de `rotate` e:

1. rejeitar valor ausente;
2. calcular o hash;
3. buscar o registro com lock pessimista;
4. rejeitar token inexistente, revogado ou expirado;
5. detectar reutilizacao;
6. marcar o token atual como usado;
7. gerar e persistir o proximo token;
8. reconstruir a `Authentication` do usuario;
9. emitir access token e cookie novos no controller.

### Expiracao absoluta

O token substituto herda `expiresAt` do primeiro token da familia. A rotacao nao
adiciona mais duas horas.

```text
login as 10:00 -> familia expira as 12:00
refresh as 11:50 -> novo token ainda expira as 12:00
```

Isso impede que uma familia seja renovada indefinidamente apenas por atividade.

### Lock pessimista

`findByTokenHashForUpdate` usa `PESSIMISTIC_WRITE`. Duas transacoes nao devem
consumir o mesmo token simultaneamente e ambas criarem sucessores validos.

Sem controle de concorrencia, o requisito de uso unico seria violado.

### Deteccao de reutilizacao

Se um token marcado como usado reaparece, existe a possibilidade de copia ou
roubo. A aplicacao revoga toda a familia e lanca
`RefreshTokenReuseException`.

A transacao usa:

```text
dontRollbackOn = RefreshTokenReuseException.class
```

Sem essa configuracao, a excecao poderia desfazer justamente a revogacao criada
para responder ao incidente.

### Concorrencia legitima

Uma politica estrita pode interpretar dois refreshes simultaneos do mesmo
cliente como reutilizacao. Abas concorrentes e repeticao automatica de rede
precisam ser coordenadas pelo frontend. Em producao, deve-se avaliar uma janela
de tolerancia cuidadosamente projetada, sem permitir reutilizacao ampla.

## 14. Cookie do refresh token

O cookie atual usa:

```text
nome     = refresh_token
HttpOnly = true
Secure   = configuravel
SameSite = Lax
Path     = /auth/jwt
Max-Age  = tempo restante da familia
```

### HttpOnly

Impede leitura direta por JavaScript. Isso reduz a exposicao do refresh token em
caso de XSS, embora XSS ainda possa executar operacoes no contexto da pagina.

### Secure

Restringe o envio a HTTPS. Esta `false` por padrao apenas para permitir o
laboratorio em `http://localhost`. Em producao deve ser `true`.

### SameSite=Lax

Reduz alguns envios cross-site. Nao substitui CSRF e precisa ser reavaliado se
frontend e backend estiverem em topologia realmente cross-site.

### Path

Restringe o cookie as rotas `/auth/jwt`. Ele nao precisa ser enviado para toda a
aplicacao.

O cookie de remocao deve repetir nome, path e atributos relevantes e usar
`Max-Age=0`. Caso o path seja diferente, o navegador pode manter o cookie
original.

## 15. Por que existe CSRF neste fluxo

Um Bearer token enviado manualmente no header `Authorization` normalmente nao e
anexado automaticamente pelo navegador. Se todo o mecanismo usasse somente esse
header, o risco classico de CSRF seria menor.

Este laboratorio, porem, guarda o refresh token em cookie. Navegadores enviam
cookies automaticamente. Login, refresh e logout alteram estado ou usam uma
credencial baseada em cookie, portanto permanecem protegidos por CSRF.

O padrao usado e semelhante ao double-submit cookie:

```text
cookie: JWT-XSRF-TOKEN=<valor>
header: X-JWT-XSRF-TOKEN=<mesmo valor>
```

O cookie CSRF nao e `HttpOnly` porque o frontend precisa le-lo para montar o
header. O segredo protegido por `HttpOnly` e o refresh token.

O `CsrfTokenRequestAttributeHandler` informa ao `CsrfFilter` como resolver o
token apresentado. O endpoint `/csrf` materializa o token adiado e permite ao
cliente iniciar o fluxo.

No logout, os dois cookies precisam ser removidos. Os dois headers `Set-Cookie`
devem coexistir; um nao pode sobrescrever o outro.

## 16. CORS

CORS e uma politica do navegador para chamadas entre origens. Ele nao autentica
usuarios e nao protege clientes que nao sejam navegadores.

`JwtCorsConfig` permite:

- uma origem configuravel;
- metodos `GET`, `POST` e `OPTIONS`;
- headers `Content-Type`, `Authorization` e `X-JWT-XSRF-TOKEN`;
- credenciais, para envio dos cookies;
- cache de preflight por uma hora.

Como `allowCredentials` esta habilitado, nao se deve usar origem curinga `*`.
O frontend precisa enviar credenciais nas chamadas que dependem dos cookies.

CSRF e CORS resolvem problemas diferentes:

```text
CORS -> quais origens podem ler/enviar chamadas pelo navegador
CSRF -> prova que uma operacao com cookie partiu do cliente esperado
```

## 17. Logout e seus limites

O logout revoga a familia do refresh token e remove os cookies. Isso impede
novas renovacoes daquela familia.

Ele nao invalida imediatamente um access token ja emitido. O JWT continua valido
ate seu `exp`, atualmente por no maximo 15 minutos.

Esse comportamento e uma consequencia do modelo stateless. Invalidacao imediata
exigiria alguma forma de estado consultado por requisicao, por exemplo:

- denylist por `jti`;
- versao de credencial por usuario;
- introspeccao em um servidor de autorizacao;
- access tokens ainda mais curtos;
- troca para tokens opacos stateful.

Cada opcao reduz parte do beneficio stateless e adiciona custo operacional.

## 18. Respostas de erro

O projeto usa `ProblemDetail` para manter o contrato de erro consistente.

### 401 Unauthorized

Indica ausencia ou invalidade de autenticacao:

- access token ausente;
- assinatura invalida;
- token expirado;
- issuer ou audience incorretos;
- credenciais de login invalidas;
- refresh token ausente, desconhecido, expirado, revogado ou reutilizado.

`JwtAuthenticationEntryPoint` trata rejeicoes do Bearer token na cadeia de
filtros. O `GlobalExceptionHandler` trata falhas lancadas dentro dos controllers,
como login e refresh.

### 403 Forbidden

Indica autenticacao sem permissao suficiente ou rejeicao de outra protecao:

- token CSRF ausente ou incorreto;
- origem CORS nao permitida.

`JwtAccessDeniedHandler` padroniza as rejeicoes da chain.

Nao se deve responder com detalhes criptograficos, stack trace, existencia de
usuario ou estado interno do refresh token.

## 19. Mapa dos componentes

| Componente | Responsabilidade |
|---|---|
| `SecurityConfigByJwt` | Delimita a chain, define autorizacao e conecta Resource Server, CSRF, CORS e handlers |
| `JwtKeyConfig` | Cria chaves RSA, encoder, decoder e validadores |
| `JwtSecurityConstants` | Centraliza issuer e audience |
| `JwtTokenService` | Monta claims e assina access tokens |
| `JwtAuthenticationConverter` | Converte `authorities` em permissoes do Spring |
| `AuthByJwtController` | Expoe login, refresh, logout, CSRF e dados da autenticacao |
| `JwtLoginResponseDTO` | Define a resposta publica do access token |
| `JwtUserResponseDTO` | Define os dados e roles entregues ao frontend |
| `JwtCsrfConfig` | Configura cookie, header e path exclusivos do CSRF JWT |
| `JwtCorsConfig` | Limita chamadas cross-origin do navegador |
| `JwtAuthenticationEntryPoint` | Retorna `401` para Bearer ausente ou invalido |
| `JwtAccessDeniedHandler` | Retorna `403` para acesso proibido |
| `JwtRefreshTokenCodec` | Gera refresh token e calcula seu hash |
| `JwtRefreshToken` | Modela familia, expiracao, uso e revogacao |
| `JwtRefreshTokenRepository` | Busca com lock e revoga familias |
| `JwtRefreshTokenService` | Emite, rotaciona e revoga refresh tokens |
| `JwtRefreshTokenCookieFactory` | Cria e remove o cookie de refresh |
| `IssuedRefreshToken` | Transporta valor puro e expiracao na emissao |
| `RotatedRefreshToken` | Transporta refresh novo e autenticacao reconstruida |

## 20. Fases recomendadas de implementacao

Uma implementacao semelhante deve ser construida em etapas verificaveis.

### Fase 1: definir o modelo de ameacas

Antes do codigo, decidir:

- quem emite e quem consome tokens;
- quais clientes existem;
- onde os tokens serao armazenados;
- quais origens participam;
- qual impacto de roubo de access ou refresh token;
- se revogacao imediata e obrigatoria;
- quais claims e permissoes sao realmente necessarias.

### Fase 2: concluir identidade e senha

Implementar `UserDetailsService`, `UserDetails`, `PasswordEncoder` e
`AuthenticationManager`. JWT nao corrige um login inseguro.

### Fase 3: escolher algoritmo e ciclo de chaves

Configurar encoder, decoder, algoritmo permitido, issuer, audience, expiracao e
armazenamento das chaves. Nunca aceitar o algoritmo indicado pelo token sem uma
politica fixa no servidor.

### Fase 4: emitir access token minimo

Adicionar apenas claims necessarias, um identificador estavel, authorities,
`iat`, `exp`, `iss`, `aud` e `jti`. Testar emissao e validacao.

### Fase 5: configurar Resource Server

Criar chain dedicada, stateless, converter authorities e proteger um endpoint
simples. Testar token valido, expirado, adulterado e assinado por outra chave.

### Fase 6: implementar login

Autenticar credenciais antes de emitir qualquer token. Padronizar `401` e evitar
enumeracao de contas.

### Fase 7: projetar refresh token

Definir aleatoriedade, hash, TTL, cookie, tabela, familia, uso unico, lock e
politica de revogacao. Nao adicionar refresh como um detalhe tardio.

### Fase 8: implementar rotacao e reutilizacao

Consumir cada token uma vez, preservar expiracao absoluta, revogar a familia em
caso de reutilizacao e garantir que a revogacao nao sofra rollback.

### Fase 9: configurar CSRF e CORS

Se alguma credencial usa cookie, modelar CSRF explicitamente. Configurar apenas
origens, metodos e headers necessarios.

### Fase 10: implementar logout

Revogar a familia, remover cookies e permitir logout com access token expirado.
Manter CSRF obrigatorio.

### Fase 11: criar testes por camada

Testar dominio, criptografia, service, controller e a chain real. Testes somente
do controller nao provam que os filtros estao corretos.

### Fase 12: preparar operacao

Planejar chaves persistentes, rotacao, limpeza da tabela, HTTPS, observabilidade,
rate limiting, resposta a incidentes e separacao de ambientes.

## 21. Principais pontos de atencao

### Nunca confiar em JWT apenas decodificado

Decodificar Base64URL nao valida assinatura. Toda decisao de seguranca deve usar
o `JwtDecoder` configurado.

### Fixar algoritmos aceitos

O servidor deve aceitar somente algoritmos planejados. Neste projeto, RS256.
Nao se deve alternar dinamicamente para `none` ou para um algoritmo inesperado.

### Validar issuer e audience

Assinatura valida nao significa que o token foi emitido para esta aplicacao.
`iss` e `aud` impedem aceitar um token legitimo no contexto errado.

### Usar HTTPS

Assinatura nao protege o token durante o transporte. Bearer token roubado pode
ser usado por quem o possuir.

### Nao registrar tokens

Access token, refresh token, cookies, senha e headers de autorizacao nao devem
aparecer em logs, traces, mensagens de erro ou ferramentas de analytics.

### Evitar access token em localStorage

`localStorage` e acessivel por JavaScript e aumenta a exposicao em caso de XSS.
Neste desenho, o frontend deve preferir manter o access token em memoria e usar
o refresh cookie `HttpOnly` para recuperar a autenticacao quando necessario.

### Manter access token curto

Quanto maior o TTL, maior a janela entre logout/revogacao e expiracao real.

### Tratar refresh token como credencial de alto valor

Ele permite emitir novos access tokens. Deve ter escopo de cookie reduzido,
`HttpOnly`, `Secure`, TTL limitado, hash no banco e rotacao.

### Controlar concorrencia

Rotacao sem lock pode criar mais de um sucessor. Clientes tambem devem evitar
varias renovacoes simultaneas.

### Nao expor estado do refresh token

Ausente, desconhecido, expirado, revogado e reutilizado retornam uma mensagem
externa generica. Detalhes podem alimentar ataques de enumeracao.

### Separar autenticacao de autorizacao

Token valido prova autenticacao. Acesso a uma operacao ainda depende das
authorities e das regras do recurso.

### Revisar multiplas SecurityFilterChains

Somente a primeira chain correspondente processa a requisicao. Matchers
sobrepostos ou uma rota fora de todas as chains podem produzir falhas graves.

### Nao confundir CORS com seguranca da API

Clientes HTTP fora do navegador ignoram CORS. A API ainda precisa de
autenticacao e autorizacao completas.

## 22. Onde esse modelo se encaixa melhor

### APIs consumidas por SPA

Funciona bem quando uma SPA chama uma API e envia o access token no header. O
refresh cookie reduz a exposicao da credencial de longa duracao.

### Aplicativos mobile e desktop

Esses clientes podem guardar credenciais em armazenamento seguro do sistema e
usar access tokens em APIs. O transporte e armazenamento precisam ser adaptados;
cookie e CSRF nao sao necessariamente a melhor opcao fora do navegador.

### APIs distribuidas e microsservicos

Varios resource servers podem validar localmente um JWT assinado usando chave
publica, sem consultar o emissor a cada requisicao. Isso exige gestao real de
chaves, `kid`, distribuicao de JWKS e limites claros de audiencia.

### Integracoes entre servicos

JWT pode transportar identidade tecnica e escopos entre servicos. Para esse
caso, normalmente se usa OAuth 2.0 Client Credentials e um Authorization Server,
nao um endpoint proprio de email e senha.

### Ambientes com latencia ou disponibilidade distribuidas

Validacao local reduz dependencia de consulta central em cada chamada. A troca
e aceitar que revogacao imediata se torna mais dificil.

## 23. Onde JWT pode nao ser a melhor escolha

### Aplicacao web monolitica tradicional

Se frontend e backend pertencem ao mesmo servidor, sessao HTTP pode ser mais
simples, oferecer revogacao imediata e reduzir a quantidade de codigo de tokens.

### Sistemas que exigem revogacao instantanea

Aplicacoes financeiras ou operacoes muito sensiveis podem precisar consultar
estado atual em cada requisicao. Token opaco com introspeccao ou sessao pode ser
mais coerente.

### Sistemas pequenos sem consumidores distribuidos

JWT adiciona gestao de chaves, claims, expiracao, refresh, armazenamento no
cliente e riscos de vazamento. Nao deve ser escolhido apenas por ser popular.

### Dados de autorizacao que mudam constantemente

Claims ficam desatualizadas ate nova emissao. Se cada decisao depende do estado
mais recente, consultas stateful podem ser inevitaveis.

## 24. JWT, OAuth 2.0 e OpenID Connect

Esses conceitos nao sao sinonimos:

```text
JWT            -> formato de token
OAuth 2.0      -> framework de autorizacao delegada
OpenID Connect -> camada de identidade sobre OAuth 2.0
```

OAuth pode usar tokens opacos. JWT pode ser usado fora de OAuth. Um ID Token do
OpenID Connect tem finalidade diferente de um access token e nao deve ser usado
como substituto indiscriminado.

Este laboratorio possui emissao propria depois de login com email e senha. Ele
nao e um Authorization Server OAuth 2.0 completo.

## 25. Configuracoes atuais

```properties
app.security.jwt.allowed-origin=${JWT_FRONTEND_ORIGIN:http://localhost:5173}
app.security.jwt.refresh-token-ttl=${JWT_REFRESH_TOKEN_TTL:2h}
app.security.jwt.refresh-cookie-secure=${JWT_REFRESH_COOKIE_SECURE:false}
```

Valores recomendados por ambiente:

```text
desenvolvimento local:
JWT_FRONTEND_ORIGIN=http://localhost:5173
JWT_REFRESH_COOKIE_SECURE=false

ambiente com HTTPS:
JWT_FRONTEND_ORIGIN=https://frontend.exemplo.com
JWT_REFRESH_COOKIE_SECURE=true
```

O TTL do access token esta atualmente fixado em 15 minutos no
`JwtTokenService`. Em uma evolucao, ele pode virar propriedade validada por
ambiente.

## 26. Roteiro de teste manual

### 1. Obter CSRF

```http
GET http://localhost:8080/auth/jwt/csrf
```

Guardar o cookie `JWT-XSRF-TOKEN` e enviar seu valor como
`X-JWT-XSRF-TOKEN` nos proximos POSTs.

### 2. Fazer login

```http
POST http://localhost:8080/auth/jwt/login
Content-Type: application/json
X-JWT-XSRF-TOKEN: <valor>
```

```json
{
  "email": "rafael@email.com",
  "password": "123456"
}
```

Guardar o `accessToken`. O cliente de API deve manter os cookies recebidos.

### 3. Acessar recurso

```http
GET http://localhost:8080/auth/jwt/sobre
Authorization: Bearer <accessToken>
```

### 4. Renovar

```http
POST http://localhost:8080/auth/jwt/refresh
X-JWT-XSRF-TOKEN: <valor>
```

O cookie de refresh e enviado automaticamente pelo gerenciador de cookies. A
resposta deve trazer access token e refresh cookie novos.

### 5. Fazer logout

```http
POST http://localhost:8080/auth/jwt/logout
X-JWT-XSRF-TOKEN: <valor>
```

Esperado:

- status `204`;
- `refresh_token` removido;
- `JWT-XSRF-TOKEN` removido;
- familia revogada no banco.

### 6. Confirmar revogacao

Obter um CSRF novo e tentar `/refresh`. Sem refresh cookie valido, a resposta
deve ser `401`.

Um access token emitido antes do logout ainda pode funcionar ate seu `exp`. Isso
e esperado no desenho atual.

## 27. Cobertura automatizada

Os testes do pacote JWT verificam:

- emissao e claims do access token;
- assinatura RS256;
- issuer e audience;
- token expirado e assinatura de outra chave;
- conversao de authorities;
- geracao e hash do refresh token;
- invariantes da entidade de refresh;
- emissao, expiracao e rotacao;
- lock e revogacao da familia;
- deteccao de reutilizacao;
- atributos e remocao do cookie;
- login, refresh e logout no controller;
- CSRF exigido em operacoes POST;
- logout e refresh sem access token;
- autorizacao de USER e ADMIN;
- CORS e preflight;
- ausencia de criacao de sessao HTTP.

Testes automatizados nao substituem validacao em HTTPS, navegador real, banco
real, multiplas instancias e cenarios de concorrencia.

## 28. Melhorias antes de producao

O fluxo essencial do laboratorio esta concluido. Para producao, ainda seria
necessario:

- persistir e proteger as chaves RSA;
- implementar `kid`, rotacao e periodo de sobreposicao;
- avaliar publicacao de JWKS;
- tornar TTL e identificadores configuraveis e validados;
- executar exclusivamente em HTTPS;
- usar cookie `Secure` em producao;
- revisar `SameSite` conforme os dominios reais;
- implementar rate limiting de login e refresh;
- registrar eventos de seguranca sem registrar tokens;
- criar limpeza periodica de refresh tokens expirados;
- definir limite de familias ou dispositivos por usuario;
- revogar familias depois de troca de senha ou bloqueio de conta;
- coordenar refreshes concorrentes no cliente;
- avaliar tolerancia controlada para repeticoes de rede;
- definir politica de revogacao imediata de access token, se exigida;
- separar configuracoes por profile;
- retirar logs DEBUG e SQL visivel em producao;
- criar metricas e alertas de falhas, reutilizacao e revogacao;
- testar PostgreSQL e concorrencia em ambiente integrado;
- criar uma chain fallback com politica explicita para rotas novas.

## 29. Checklist de revisao

Antes de considerar um fluxo JWT pronto, confirmar:

- [ ] credenciais sao validadas pelo mecanismo padrao do Spring;
- [ ] senha nunca entra no token;
- [ ] algoritmo aceito esta fixado;
- [ ] assinatura, `iss`, `aud`, `exp` e demais tempos sao validados;
- [ ] access token possui TTL curto;
- [ ] claims contem somente o necessario;
- [ ] authorities usam um formato consistente;
- [ ] refresh token tem alta entropia;
- [ ] banco armazena somente o hash do refresh token;
- [ ] refresh token e de uso unico e rotacionado;
- [ ] reutilizacao revoga a familia;
- [ ] rotacao possui controle de concorrencia;
- [ ] cookie de refresh usa `HttpOnly`, `Secure`, `SameSite` e path adequado;
- [ ] operacoes baseadas em cookie possuem CSRF;
- [ ] CORS permite somente origens e headers necessarios;
- [ ] logout remove cookies e revoga renovacao;
- [ ] limitacao de revogacao do access token esta documentada;
- [ ] erros `401` e `403` nao vazam detalhes;
- [ ] tokens e senhas nao aparecem em logs;
- [ ] chaves possuem armazenamento e rotacao seguros;
- [ ] testes cobrem filtros reais, nao apenas controllers.

## 30. Referencias principais

- [RFC 7519 - JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
- [RFC 8725 - JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725)
- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700)
- [OWASP JSON Web Token Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [OWASP OAuth 2.0 Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)
- [Spring Security - OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security - CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security - CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [Spring Security - Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
