package com.rafael.autenticacao.Authentication.session.config;

import com.rafael.autenticacao.Authentication.session.controller.AuthBySessionController;
import com.rafael.autenticacao.Authentication.session.cors.SessionCorsConfig;
import com.rafael.autenticacao.Authentication.session.handler.SessionAccessDeniedHandler;
import com.rafael.autenticacao.Authentication.session.handler.SessionAuthenticationEntryPoint;
import com.rafael.autenticacao.Authentication.shared.userdetails.UsuarioDetails;
import com.rafael.autenticacao.Usuario.Controller.UsuarioController;
import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthBySessionController.class,
        UsuarioController.class
}, excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class,
        Saml2RelyingPartyAutoConfiguration.class
})
@Import({
        SecurityConfigBySession.class,
        SessionCorsConfig.class,
        SessionAuthenticationEntryPoint.class,
        SessionAccessDeniedHandler.class
})
class SessionSecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @Test
    void anonymousUserShouldReceiveUnauthorizedOnProtectedRoute() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/usuarios"));
    }

    @Test
    void oauth2LoginShouldNotAuthenticateSessionRoute() throws Exception {
        var session = new MockHttpSession();
        var oauth2Context = SecurityContextHolder.createEmptyContext();
        oauth2Context.setAuthentication(new TestingAuthenticationToken(
                "oauth2-user",
                null,
                "ROLE_USER"
        ));
        session.setAttribute("SPRING_SECURITY_CONTEXT_OAUTH2", oauth2Context);

        mockMvc.perform(get("/auth/session/sobre").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutAdminRoleShouldReceiveForbidden() throws Exception {
        mockMvc.perform(get("/usuarios").with(user("rafael").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/usuarios"));
    }

    @Test
    void adminShouldAccessUserRoutes() throws Exception {
        when(usuarioService.findAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void publicPostRouteShouldStillRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/auth/session/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Rafael",
                                  "email": "rafael@email.com",
                                  "password": "123456",
                                  "age": 30
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginShouldAcceptTokenIssuedByCsrfEndpoint() throws Exception {
        var csrfResult = mockMvc.perform(get("/auth/session/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(
                        "rafael@email.com",
                        null,
                        "ROLE_USER"
                ));

        mockMvc.perform(post("/auth/session/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "rafael@email.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Usuario logado com sucesso"))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
    }

    @Test
    void authenticatedUserShouldReceiveIdentityAndRolesFromSession() throws Exception {
        UUID userId = UUID.randomUUID();
        var userDetails = new UsuarioDetails(new Entidade(
                userId,
                "Rafael",
                "rafael@email.com",
                "password-hash",
                30,
                Role.USER
        ));

        mockMvc.perform(get("/auth/session/sobre").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("rafael@email.com"))
                .andExpect(jsonPath("$.name").value("Rafael"))
                .andExpect(jsonPath("$.email").value("rafael@email.com"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }
}
