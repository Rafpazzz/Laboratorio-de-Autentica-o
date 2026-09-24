package com.rafael.autenticacao.Authentication.oauth2.config;

import com.rafael.autenticacao.Authentication.oauth2.controller.AuthByOAuth2Controller;
import com.rafael.autenticacao.Authentication.oauth2.cors.OAuth2CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthByOAuth2Controller.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                Saml2RelyingPartyAutoConfiguration.class
        }
)
@Import({
        SecurityConfigByOAuth2.class,
        KeycloakRealmRoleAuthoritiesMapper.class,
        OAuth2CorsConfig.class,
        OAuth2SecurityFilterChainTest.OAuth2ClientTestConfiguration.class
})
class OAuth2SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authorizationEndpointShouldStartOidcAuthorizationCodeFlow() throws Exception {
        var result = mockMvc.perform(get("/auth/oauth2/authorization/keycloak"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();

        var authorizationRequest = UriComponentsBuilder
                .fromUri(URI.create(location))
                .build();

        assertThat(authorizationRequest.getScheme()).isEqualTo("https");
        assertThat(authorizationRequest.getHost()).isEqualTo("idp.example");
        assertThat(authorizationRequest.getPath()).isEqualTo("/authorize");
        assertThat(authorizationRequest.getQueryParams().getFirst("response_type"))
                .isEqualTo("code");
        assertThat(authorizationRequest.getQueryParams().getFirst("client_id"))
                .isEqualTo("test-client");
        assertThat(authorizationRequest.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("http://localhost/auth/oauth2/callback/keycloak");
        assertThat(authorizationRequest.getQueryParams().getFirst("scope"))
                .contains("openid", "profile", "email");
        assertThat(authorizationRequest.getQueryParams().getFirst("state")).isNotBlank();
        assertThat(authorizationRequest.getQueryParams().getFirst("nonce")).isNotBlank();
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void authenticatedOidcUserShouldReceiveOnlySelectedIdentityClaims() throws Exception {
        mockMvc.perform(get("/auth/oauth2/sobre")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .subject("keycloak-subject")
                                        .claim("preferred_username", "rafael")
                                        .claim("name", "Rafael Silva")
                                        .claim("email", "rafael@email.com")
                                        .claim("email_verified", true)
                                )
                                .authorities(
                                        new SimpleGrantedAuthority("OIDC_USER"),
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("keycloak-subject"))
                .andExpect(jsonPath("$.username").value("rafael"))
                .andExpect(jsonPath("$.name").value("Rafael Silva"))
                .andExpect(jsonPath("$.email").value("rafael@email.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.authorities", containsInAnyOrder("ROLE_USER")))
                .andExpect(jsonPath("$.idToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.claims").doesNotExist());
    }

    @Test
    void anonymousUserShouldNotAccessOidcIdentity() throws Exception {
        mockMvc.perform(get("/auth/oauth2/sobre"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Autenticacao OAuth2 necessaria"))
                .andExpect(jsonPath("$.instance").value("/auth/oauth2/sobre"));
    }

    @Test
    void sessionLoginShouldNotAuthenticateOAuth2Route() throws Exception {
        var session = new MockHttpSession();
        var sessionContext = SecurityContextHolder.createEmptyContext();
        sessionContext.setAuthentication(new TestingAuthenticationToken(
                "session-user",
                null,
                "ROLE_USER"
        ));
        session.setAttribute("SPRING_SECURITY_CONTEXT_SESSION", sessionContext);

        mockMvc.perform(get("/auth/oauth2/sobre").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfEndpointShouldProvideTokenForLogout() throws Exception {
        mockMvc.perform(get("/auth/oauth2/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void logoutWithoutCsrfTokenShouldBeRejected() throws Exception {
        mockMvc.perform(post("/auth/oauth2/logout")
                        .with(oidcLogin()
                                .clientRegistration(keycloakRegistration())
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutShouldClearOnlyOAuth2ContextAndRedirectToProvider() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT_SESSION", "session-context");

        var result = mockMvc.perform(post("/auth/oauth2/logout")
                        .session(session)
                        .with(oidcLogin()
                                .clientRegistration(keycloakRegistration())
                                .idToken(token -> token
                                        .tokenValue("id-token-value")
                                        .subject("keycloak-subject")
                                )
                        )
                        .with(csrf())
                )
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();

        var logoutRequest = UriComponentsBuilder
                .fromUri(URI.create(location))
                .build();

        assertThat(logoutRequest.getScheme()).isEqualTo("https");
        assertThat(logoutRequest.getHost()).isEqualTo("idp.example");
        assertThat(logoutRequest.getPath()).isEqualTo("/logout");
        assertThat(logoutRequest.getQueryParams().getFirst("id_token_hint"))
                .isEqualTo("id-token-value");
        assertThat(logoutRequest.getQueryParams().getFirst("post_logout_redirect_uri"))
                .isEqualTo("http://localhost/auth/oauth2/logged-out");
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT_SESSION"))
                .isEqualTo("session-context");
    }

    @Test
    void loggedOutEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/auth/oauth2/logged-out"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        "http://localhost:5173/"
                ));
    }

    @Test
    void preflightShouldAllowConfiguredOAuth2OriginWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/auth/oauth2/logout")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Content-Type, X-CSRF-TOKEN"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OAuth2ClientTestConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(keycloakRegistration());
        }

        @Bean
        OAuth2AuthorizedClientService authorizedClientService(
                ClientRegistrationRepository clientRegistrationRepository
        ) {
            return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        }

        @Bean
        OAuth2AuthorizedClientRepository authorizedClientRepository(
                OAuth2AuthorizedClientService authorizedClientService
        ) {
            return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(
                    authorizedClientService
            );
        }

    }

    private static ClientRegistration keycloakRegistration() {
        return ClientRegistration
                .withRegistrationId("keycloak")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/auth/oauth2/callback/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://idp.example/authorize")
                .tokenUri("https://idp.example/token")
                .jwkSetUri("https://idp.example/jwks")
                .userInfoUri("https://idp.example/userinfo")
                .userNameAttributeName("sub")
                .providerConfigurationMetadata(Map.of(
                        "end_session_endpoint", "https://idp.example/logout"
                ))
                .clientName("Keycloak")
                .build();
    }
}
