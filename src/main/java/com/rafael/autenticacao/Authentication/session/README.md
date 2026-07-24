# Autenticacao por sessao

Esta pasta registra a etapa de autenticação por sessão do laboratório. O objetivo desta etapa foi entender o fluxo stateful do Spring Security usando email, senha, `SecurityContext`, `HttpSession` e cookie `JSESSIONID`.

## Objetivo

Implementar um login em que o servidor mantém o estado da autenticação.

Nesse modelo, o cliente envia email e senha uma vez. Se as credenciais forem válidas, o servidor cria uma sessão HTTP e devolve um cookie `JSESSIONID`. Nas próximas requisições, o cliente envia esse cookie e o servidor recupera a autenticação da sessão.

## Fluxo implementado

```text
POST /auth/loginBySession
        ↓
AuthBySessionController recebe email e senha
        ↓
UsernamePasswordAuthenticationToken representa a tentativa de login
        ↓
AuthenticationManager autentica as credenciais
        ↓
UsuarioDetailsService busca o usuário pelo email
        ↓
PasswordEncoder compara senha pura com hash BCrypt
        ↓
UsuarioDetails expõe username, password e authorities
        ↓
SecurityContext guarda o Authentication autenticado
        ↓
SecurityContextRepository salva o contexto na HttpSession
        ↓
Cliente recebe o cookie JSESSIONID
```

## Componentes principais

### AuthBySessionController

Responsável pelos endpoints de sessão:

```text
POST /auth/loginBySession
POST /auth/logoutBySession
```

No login, o controller não compara senha manualmente. Ele cria um `UsernamePasswordAuthenticationToken` e delega a autenticação para o `AuthenticationManager`.

No logout, o controller limpa o `SecurityContextHolder` e invalida a sessão HTTP atual quando ela existe.

### SecurityConfigBySession

Configura a cadeia de segurança da etapa de sessão.

Regras atuais:

```text
POST /usuarios        público
POST /auth/**         público
demais rotas          exigem autenticação
```

Também registra:

```text
AuthenticationManager
SecurityContextRepository
```

O `SecurityContextRepository` usado é o `HttpSessionSecurityContextRepository`, que salva o contexto de segurança na sessão HTTP.

### UsuarioDetails

Adapta a entidade de usuário para o contrato `UserDetails` do Spring Security.

Mapeamento:

```text
Entidade.email        -> getUsername()
Entidade.password     -> getPassword()
Entidade.role         -> getAuthorities()
```

As roles são convertidas para authorities com prefixo `ROLE_`:

```text
USER  -> ROLE_USER
ADMIN -> ROLE_ADMIN
```

### UsuarioDetailsService

Implementa `UserDetailsService`.

Sua responsabilidade é buscar o usuário pelo email informado no login e retornar um `UsuarioDetails`.

Se o usuário não existir, lança `UsernameNotFoundException`, que o Spring Security entende como falha de autenticação.

## Login

Endpoint:

```text
POST /auth/loginBySession
```

Body:

```json
{
  "email": "rafael@email.com",
  "password": "123456"
}
```

Se as credenciais forem válidas:

```text
200 OK
Set-Cookie: JSESSIONID=...
```

Se email ou senha forem inválidos, o Spring Security rejeita a autenticação.

## Logout

Endpoint:

```text
POST /auth/logoutBySession
```

O logout executa dois passos:

```text
SecurityContextHolder.clearContext()
request.getSession(false).invalidate()
```

O primeiro limpa a autenticação da requisição atual. O segundo invalida a sessão HTTP, fazendo o cookie antigo deixar de autenticar.

## Como testar manualmente

### 1. Acessar rota protegida sem login

```text
GET /usuarios
```

Resultado esperado:

```text
401 Unauthorized
```

### 2. Fazer login

```text
POST /auth/loginBySession
```

Resultado esperado:

```text
200 OK
JSESSIONID criado
```

### 3. Acessar rota protegida com cookie

```text
GET /usuarios
```

Resultado esperado:

```text
200 OK
```

### 4. Fazer logout

```text
POST /auth/logoutBySession
```

Resultado esperado:

```text
200 OK
```

### 5. Repetir acesso com cookie antigo

```text
GET /usuarios
```

Resultado esperado:

```text
401 Unauthorized
```

## Pontos de segurança

Senha nunca é comparada manualmente no controller. A comparação é responsabilidade do Spring Security com `PasswordEncoder.matches`.

O hash da senha é gerado no cadastro com BCrypt.

O cliente não escolhe a role no cadastro. A role padrão é definida pelo backend.

O `SecurityContext` precisa ser salvo no repositório de contexto para que a autenticação sobreviva entre requisições.

O logout precisa invalidar a sessão para que o `JSESSIONID` antigo deixe de funcionar.

## Limites desta etapa

CSRF ainda está desabilitado para facilitar os testes via cliente HTTP.

Isso é temporário. Como sessão usa cookie, a próxima etapa do laboratório deve estudar CSRF e CORS.

JWT, access token, refresh token, OAuth2 e SAML ainda não fazem parte desta etapa.
