# Atualizacoes futuras

Este documento concentra melhorias que nao fazem parte do objetivo atual do
laboratorio. Cada forma de autenticacao deve ser concluida primeiro em seu fluxo
essencial. Os ajustes de infraestrutura, operacao e endurecimento para producao
podem ser executados posteriormente sem bloquear o estudo dos proximos
mecanismos.

## Login por sessao

### Estado atual

O fluxo essencial de autenticacao por sessao esta concluido:

- registro de usuario sempre com a role `USER`;
- armazenamento da senha com BCrypt;
- autenticacao com `AuthenticationManager`;
- persistencia do `SecurityContext` na `HttpSession`;
- cookie `JSESSIONID` com `HttpOnly` e `SameSite=Lax`;
- protecao contra session fixation;
- protecao CSRF baseada em cookie;
- rotacao do token CSRF depois do login;
- remocao do token CSRF no logout;
- CORS limitado a uma origem configuravel;
- autorizacao de `/usuarios/**` para `ADMIN`;
- respostas `401` e `403` usando `ProblemDetail`;
- testes unitarios e de integracao da `SecurityFilterChain`.

Esse estado e suficiente para encerrar a etapa de sessao no laboratorio e
iniciar o estudo da proxima forma de autenticacao.

### Melhorias para producao

#### Cobertura final das rotas

- A `SecurityFilterChain` de menor prioridade com `denyAll()` ja foi criada.
- Rotas sem mecanismo proprietario retornam `404` e nao alcancam controllers.
- Revisar a propriedade de cada rota quando novas autenticacoes forem
  adicionadas.
- Decidir se `/usuarios/**` continuara exclusiva da sessao ou se tambem podera
  ser acessada por administradores autenticados por outro mecanismo.

Uma requisicao que nao corresponde a nenhuma `SecurityFilterChain` nao e
protegida pelo Spring Security. A chain de fallback evita que uma nova rota seja
publicada sem uma decisao explicita de seguranca.

#### Politica de senha

- Definir comprimentos minimo e maximo.
- Permitir senhas longas e compativeis com gerenciadores de senha.
- Rejeitar senhas comuns ou conhecidas em vazamentos.
- Avaliar um medidor de forca na futura interface.
- Planejar atualizacao gradual do algoritmo ou do custo do hash.
- Avaliar `DelegatingPasswordEncoder` para facilitar futuras migracoes.

O BCrypt atual protege o armazenamento, mas o DTO de registro ainda exige
somente que a senha nao esteja vazia. Essa permissividade e intencional durante
o laboratorio.

#### Cookies e HTTPS

- Executar toda a aplicacao em HTTPS.
- Definir `SESSION_COOKIE_SECURE=true` em producao.
- Manter `HttpOnly` no cookie de sessao.
- Revisar `SameSite` de acordo com a topologia real do frontend.
- Nunca usar `SameSite=None` sem o atributo `Secure`.
- Avaliar o prefixo `__Host-` para o cookie de sessao.
- Confirmar os atributos finais de `JSESSIONID` e `XSRF-TOKEN` no navegador.

O valor `Secure=false` e necessario para os testes atuais em
`http://localhost`, mas nao deve ser usado em uma implantacao real.

#### Politica de duracao da sessao

- Reavaliar o timeout ocioso de duas horas conforme o risco do ambiente.
- Definir um tempo maximo absoluto para a sessao.
- Exigir nova autenticacao antes de operacoes sensiveis.
- Avaliar encerramento de outras sessoes depois de troca de senha.
- Definir uma politica para sessoes simultaneas do mesmo usuario.

As duas horas configuradas atualmente representam timeout por inatividade. Esse
valor continua adequado ao objetivo da pagina de estudos do laboratorio.

#### Escalabilidade

- Persistir sessoes em um armazenamento compartilhado, como Redis, caso existam
  varias instancias da aplicacao.
- Avaliar Spring Session para abstrair o armazenamento.
- Definir expiracao e limpeza das sessoes no armazenamento compartilhado.
- Evitar dependencia de sticky sessions no balanceador quando houver uma
  solucao compartilhada.

O armazenamento em memoria do servidor e suficiente enquanto o laboratorio
executar em uma unica instancia.

#### Protecao do login

- Aplicar rate limiting nas tentativas de login.
- Definir bloqueio temporario ou atraso progressivo depois de falhas repetidas.
- Manter mensagens de credenciais invalidas sem revelar se o email existe.
- Registrar eventos relevantes de login, logout e falha de autenticacao.
- Considerar autenticacao multifator em um contexto de producao.

#### Configuracao e observabilidade

- Desativar `spring.jpa.show-sql` em producao.
- Remover o nivel `DEBUG` do Spring Security em producao.
- Evitar logs contendo credenciais, tokens, cookies ou identificadores de
  sessao.
- Separar propriedades por profiles de desenvolvimento, teste e producao.
- Criar metricas e alertas para falhas de login e respostas `401` e `403`.

#### Testes de ambiente

- Testar expiracao real por inatividade.
- Testar o limite absoluto da sessao quando ele for implementado.
- Validar cookies em HTTPS usando um navegador real.
- Testar CORS com o dominio final do frontend.
- Testar sessoes simultaneas.
- Testar reinicio e multiplas instancias com armazenamento compartilhado.
- Executar testes com PostgreSQL em container.

## Proximas formas de autenticacao

As proximas secoes devem ser preenchidas conforme cada mecanismo for estudado:

### JWT com access e refresh token

O fluxo essencial esta concluido:

- access token JWT assinado com RS256;
- validacao de assinatura, issuer, audience e expiracao;
- autorizacao baseada em roles;
- refresh token opaco armazenado como hash;
- rotacao de uso unico com lock pessimista;
- deteccao de reutilizacao e revogacao da familia;
- expiracao absoluta de duas horas;
- cookies protegidos e CSRF dedicado;
- CORS restrito a origem configuravel;
- login, refresh e logout stateless;
- respostas `401` e `403` com `ProblemDetail`;
- testes unitarios e da `SecurityFilterChain`.

As decisoes, os fluxos e as melhorias necessarias antes de producao estao
documentados em
`src/main/java/com/rafael/autenticacao/Authentication/jwt/README.md`.

### OAuth2 e OpenID Connect

O fluxo essencial esta funcional:

- Keycloak executando localmente como OpenID Provider;
- client confidencial com secret externo ao repositorio;
- Authorization Code Flow com PKCE;
- scopes `openid`, `profile` e `email`;
- validacao OIDC delegada ao Spring Security;
- identidade local mantida em `HttpSession`;
- resposta `/sobre` sem exposicao de tokens;
- realm roles convertidas por allowlist;
- autorizacao para `USER` e `ADMIN`;
- protecao CSRF no logout;
- CORS limitado a uma origem configuravel;
- logout da sessao local e da sessao SSO do Keycloak;
- testes do login, autorizacao, logout, roles e CORS.

As decisoes e o fluxo completo estao documentados em
`src/main/java/com/rafael/autenticacao/Authentication/oauth2/README.md`.

#### Handlers de seguranca

- Criar um `AuthenticationFailureHandler` especifico para falhas no callback.
- O `AccessDeniedHandler` com resposta `ProblemDetail` para `403` ja existe.
- O endpoint `/sobre` sem sessao retorna `401` em vez de redirecionar.
- A rota explicita de inicio do login preserva o redirecionamento ao Keycloak.
- Mapear cancelamento do login sem revelar detalhes internos do provider.
- Diferenciar erros recuperaveis, indisponibilidade e configuracao invalida.
- Sanitizar logs para nunca registrar code, token, cookie ou client secret.

Excecoes lancadas nos filtros OAuth2 nao passam automaticamente pelo
`GlobalExceptionHandler` do Spring MVC.

#### Transporte seguro e cookies

- Executar aplicacao, frontend e Keycloak com HTTPS.
- Habilitar `Secure` em todos os cookies usados no fluxo.
- Revisar `SameSite` conforme os dominios reais da aplicacao e do provider.
- Configurar corretamente proxy headers quando existir reverse proxy.
- Validar host, scheme e porta usados para construir callbacks e logout.
- Aplicar HSTS somente depois que todo o ambiente estiver em HTTPS.

HTTP e configuracoes de desenvolvimento sao aceitaveis apenas no ambiente
local do laboratorio.

#### Client e secrets

- Armazenar client secret em secret manager ou mecanismo equivalente.
- Definir rotacao periodica e procedimento de emergencia para o secret.
- Manter secrets diferentes por ambiente.
- Avaliar `private_key_jwt` ou mTLS quando o nivel de risco exigir.
- Nunca inserir secret em imagem, log, frontend ou repositorio Git.
- Remover a importacao direta de `.env` em deployments gerenciados.

#### Redirect URIs

- Usar redirect URIs exatas por ambiente.
- Usar post logout redirect URIs exatas.
- Evitar wildcards e dominios compartilhados sem controle.
- Revisar todas as URIs antes de publicar um novo frontend.
- Bloquear redirects controlados por parametros enviados pelo usuario.

Uma redirect URI permissiva pode permitir vazamento do authorization code.

#### Keycloak em producao

- Substituir `start-dev` por configuracao de producao.
- Usar banco de dados suportado e persistente.
- Configurar hostname, proxy e TLS de forma explicita.
- Proteger e restringir a conta administrativa inicial.
- Separar administracao do trafego publico quando possivel.
- Definir backup e restauracao de realms, usuarios e chaves.
- Planejar atualizacoes e testar compatibilidade antes de trocar versoes.
- Monitorar saude, latencia, erros e capacidade do provider.

O volume Docker atual preserva dados locais, mas nao representa estrategia de
backup ou alta disponibilidade.

#### Roles e privilegios

- Desabilitar `Full Scope Allowed` quando a politica final estiver definida.
- Limitar cada client as roles que realmente pode receber.
- Separar realm roles administrativas das roles de negocio.
- Manter allowlist tambem no codigo da aplicacao.
- Definir governanca para criacao, alteracao e remocao de roles.
- Auditar atribuicoes de `ADMIN` e alteracoes em grupos ou composites.
- Definir como mudancas de role afetam sessoes que ja foram criadas.

Uma sessao local pode continuar com authorities antigas ate novo login ou
invalidacao explicita.

#### Sessoes e escalabilidade

- Definir timeout ocioso e duracao maxima absoluta.
- Avaliar Spring Session com Redis para varias instancias.
- Invalidar sessoes locais quando a conta for bloqueada ou comprometida.
- Definir politica de sessoes simultaneas.
- Implementar revogacao administrativa das sessoes da aplicacao.
- Avaliar back-channel logout para eventos iniciados pelo provider.
- Testar indisponibilidade do armazenamento compartilhado.

O RP-Initiated Logout atual cobre o logout iniciado pelo usuario na aplicacao.
Ele nao resolve sozinho todos os eventos remotos de encerramento de sessao.

#### Persistencia de tokens do provider

- Confirmar se a aplicacao realmente precisa chamar APIs com o access token.
- Nao persistir tokens quando eles nao forem necessarios.
- Caso sejam persistidos, criptografar em repouso e limitar acesso.
- Definir expiracao e limpeza para authorized clients armazenados.
- Tratar refresh token do provider como credencial de alto valor.
- Nunca entregar refresh token do provider ao navegador sem requisito claro.
- Avaliar um armazenamento persistente de `OAuth2AuthorizedClient` em cluster.

#### Provisionamento e vinculacao de contas

- Definir se usuarios OIDC serao criados na base local.
- Usar `iss` mais `sub` como identidade externa estavel.
- Nao usar somente email como identificador permanente.
- Definir regras para alteracao e verificacao de email.
- Impedir account linking automatico inseguro entre providers.
- Definir comportamento para usuario removido ou desabilitado no Keycloak.
- Registrar consentimento e termos quando o dominio exigir.

O laboratorio atual autentica pelo provider sem provisionar uma entidade local.

#### MFA e autenticacao adaptativa

- Configurar MFA no Keycloak para perfis ou operacoes sensiveis.
- Avaliar passkeys/WebAuthn.
- Exigir nova autenticacao antes de operacoes de alto risco.
- Avaliar `acr`, `amr`, `auth_time` e `max_age` quando forem requisitos reais.
- Definir recuperacao de conta e troca segura de fatores.

#### CORS, CSRF e frontend

- Configurar `OAUTH2_FRONTEND_ORIGIN` com o dominio final de cada ambiente.
- Evitar lista ampla de origens com credenciais habilitadas.
- Revisar headers e metodos permitidos quando o contrato mudar.
- Manter CSRF em toda operacao autenticada por cookie que altere estado.
- Adotar Content Security Policy para reduzir impacto de XSS.
- Evitar expor tokens do provider ao JavaScript.
- Tratar login e logout como navegacao, nao como consumo cross-origin da pagina
  do provider por `fetch`.

#### Disponibilidade do provider

- Definir timeouts para discovery, token endpoint, UserInfo e JWKS.
- Planejar comportamento quando o Keycloak estiver indisponivel.
- Monitorar falhas de renovacao de chaves e cache de JWKS.
- Testar rotacao das chaves de assinatura.
- Evitar retry sem limite em operacoes nao idempotentes.
- Criar mensagens de indisponibilidade que nao exponham detalhes internos.

#### Auditoria e privacidade

- Registrar sucesso e falha de login sem registrar tokens.
- Registrar logout, negacao de acesso e mudanca de privilegio.
- Incluir correlation ID nos eventos.
- Proteger logs contra acesso indevido e adulteracao.
- Definir retencao conforme requisitos legais e de privacidade.
- Minimizar claims e dados pessoais solicitados ao provider.
- Nao retornar o mapa completo de claims ao frontend.

#### Testes adicionais

- Testar state ausente, alterado, expirado e reutilizado.
- Testar nonce invalido e ID Token expirado.
- Testar issuer, audience e assinatura invalidos.
- Testar authorization code reutilizado.
- Testar cancelamento do login pelo usuario.
- Testar redirect URI e post logout URI rejeitadas.
- Testar rotacao real de chaves do Keycloak.
- Testar mudanca e remocao de roles durante uma sessao.
- Testar logout local, RP-Initiated Logout e back-channel logout.
- Executar testes end-to-end em navegador real.
- Testar CORS e cookies usando os dominios finais e HTTPS.
- Testar multiplas instancias da aplicacao e do provider.

### SAML 2.0

O fluxo essencial esta concluido:

- login federado com Spring Security, OpenSAML e Keycloak;
- validacao de issuer, audience, destino, tempo e assinaturas;
- AuthnRequest e mensagens de logout assinadas pelo Service Provider;
- mapeamento de atributos e allowlist de roles;
- sessao local isolada dos outros mecanismos;
- endpoint `/sobre` com resposta de identidade controlada;
- CSRF nos logouts iniciados pelo usuario;
- CORS restrito a uma origem configuravel;
- logout local e Single Logout;
- respostas `401` e `403` com `ProblemDetail`;
- redirects de sucesso e logout para o frontend;
- testes automatizados da chain, handlers, roles, CORS e logout.

Melhorias futuras para producao:

- usar HTTPS em todas as partes e cookies com `Secure`;
- guardar a chave privada do Service Provider em secret manager ou HSM;
- planejar rotacao coordenada dos certificados do SP e do IdP;
- restringir algoritmos, bindings e duracao das assertions conforme a politica;
- configurar protecao contra brute force e MFA no Identity Provider;
- definir timeout absoluto, revogacao administrativa e armazenamento de sessao
  compartilhado;
- monitorar falhas de assinatura, clock skew e indisponibilidade do metadata;
- testar rotacao das chaves do Keycloak e multiplas instancias;
- executar testes end-to-end em navegador com os dominios finais;
- auditar logins e logouts sem registrar assertions, cookies ou dados sensiveis.

Os detalhes do protocolo e da implementacao estao em
`src/main/java/com/rafael/autenticacao/Authentication/saml/README.md`.
