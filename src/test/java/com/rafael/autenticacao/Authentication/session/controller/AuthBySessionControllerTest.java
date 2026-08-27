package com.rafael.autenticacao.Authentication.session.controller;

import com.rafael.autenticacao.Authentication.shared.dto.LoginRequestDTO;
import com.rafael.autenticacao.Authentication.shared.dto.RegisterRequestDTO;
import com.rafael.autenticacao.Shared.Exception.GlobalExceptionHandler;
import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Service.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.hasItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthBySessionControllerTest {

    private AuthBySessionController controller;
    private MockMvc mockMvc;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Mock
    private CsrfTokenRepository csrfTokenRepository;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        controller = new AuthBySessionController(
                authenticationManager,
                securityContextRepository,
                usuarioService,
                sessionAuthenticationStrategy,
                csrfTokenRepository
        );

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginShouldAuthenticateCredentialsAndSaveSecurityContext() {
        LoginRequestDTO loginRequest = new LoginRequestDTO("rafael@email.com", "123456");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        var result = controller.login(loginRequest, request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Usuario logado com sucesso");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());

        UsernamePasswordAuthenticationToken token = tokenCaptor.getValue();
        assertThat(token.getPrincipal()).isEqualTo("rafael@email.com");
        assertThat(token.getCredentials()).isEqualTo("123456");

        ArgumentCaptor<SecurityContext> contextCaptor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), any(), any());

        assertThat(contextCaptor.getValue().getAuthentication()).isSameAs(authentication);

        var loginOrder = inOrder(
                authenticationManager,
                sessionAuthenticationStrategy,
                securityContextRepository
        );
        loginOrder.verify(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
        loginOrder.verify(sessionAuthenticationStrategy)
                .onAuthentication(authentication, request, response);
        loginOrder.verify(securityContextRepository)
                .saveContext(any(SecurityContext.class), any(), any());
    }

    @Test
    void logoutShouldClearSecurityContextAndInvalidateCurrentSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var result = controller.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Logout feito");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(session.isInvalid()).isTrue();
        verify(csrfTokenRepository).saveToken(null, request, response);
    }

    @Test
    void logoutShouldNotFailWhenSessionDoesNotExist() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var result = controller.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Logout feito");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        verify(csrfTokenRepository).saveToken(null, request, response);
    }

    @Test
    void registerShouldCreateUserWithUserRole() {
        RegisterRequestDTO registerRequest = new RegisterRequestDTO(
                "Rafael",
                "rafael@email.com",
                "123456",
                30
        );

        var result = controller.register(registerRequest);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo("Usuario Criado");

        ArgumentCaptor<Entidade> userCaptor = ArgumentCaptor.forClass(Entidade.class);
        verify(usuarioService).saveUser(userCaptor.capture());

        Entidade user = userCaptor.getValue();
        assertThat(user.getId()).isNull();
        assertThat(user.getName()).isEqualTo("Rafael");
        assertThat(user.getEmail()).isEqualTo("rafael@email.com");
        assertThat(user.getPassword()).isEqualTo("123456");
        assertThat(user.getAge()).isEqualTo(30);
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void loginShouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "rafael@email.com",
                                  "password": "senha-incorreta"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.detail").value("Credenciais invalidas"))
                .andExpect(jsonPath("$.instance").value("/auth/session/login"));

        verifyNoInteractions(sessionAuthenticationStrategy);
    }

    @Test
    void loginShouldReturnBadRequestWhenBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/auth/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "email-invalido",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.detail").value("Requisicao possui campos invalidos"))
                .andExpect(jsonPath("$.fields[*].field", hasItems("email", "password")));
    }

    @Test
    void csrfShouldReturnTokenResolvedBySpringSecurity() {
        CsrfToken csrfToken = org.mockito.Mockito.mock(CsrfToken.class);

        CsrfToken result = controller.csrf(csrfToken);

        assertThat(result).isSameAs(csrfToken);
    }
}
