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

- Criar uma `SecurityFilterChain` de menor prioridade como fallback.
- Usar `denyAll()` para rotas que nao pertencam explicitamente a outra chain.
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

### Access token

A definir durante a implementacao.

### OAuth2 e OpenID Connect

A definir durante a implementacao.

### SAML 2.0

A definir durante a implementacao.

