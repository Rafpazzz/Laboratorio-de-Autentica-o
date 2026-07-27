package com.rafael.autenticacao.Authentication.session.config;

import com.rafael.autenticacao.Authentication.session.controller.AuthBySessionController;
import com.rafael.autenticacao.Authentication.session.cors.SessionCorsConfig;
import com.rafael.autenticacao.Authentication.session.handler.SessionAccessDeniedHandler;
import com.rafael.autenticacao.Authentication.session.handler.SessionAuthenticationEntryPoint;
import com.rafael.autenticacao.Usuario.Controller.UsuarioController;
import com.rafael.autenticacao.Usuario.Service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
}
