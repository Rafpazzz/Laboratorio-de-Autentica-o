package com.rafael.autenticacao.Authentication.saml.config;

import com.rafael.autenticacao.Authentication.saml.controller.AuthBySamlController;
import com.rafael.autenticacao.Authentication.saml.cors.SamlCorsConfig;
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
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthBySamlController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                Saml2RelyingPartyAutoConfiguration.class
        }
)
@Import({
        SecurityConfigBySaml.class,
        SamlCorsConfig.class,
        SamlSecurityFilterChainTest.SamlTestConfiguration.class
})
class SamlSecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUserShouldNotAccessSamlIdentity() throws Exception {
        mockMvc.perform(get("/auth/saml/sobre"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Autenticacao SAML necessaria"))
                .andExpect(jsonPath("$.instance").value("/auth/saml/sobre"));
    }

    @Test
    void sessionLoginShouldNotAuthenticateSamlRoute() throws Exception {
        var session = new MockHttpSession();
        var sessionContext = SecurityContextHolder.createEmptyContext();
        sessionContext.setAuthentication(new TestingAuthenticationToken(
                "session-user",
                null,
                "ROLE_USER"
        ));
        session.setAttribute("SPRING_SECURITY_CONTEXT_SESSION", sessionContext);

        mockMvc.perform(get("/auth/saml/sobre").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationEndpointShouldRedirectToIdentityProvider() throws Exception {
        var result = mockMvc.perform(get("/saml2/authenticate/keycloak-saml"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(location)
                .startsWith("https://idp.example/saml")
                .contains("SAMLRequest=")
                .contains("RelayState=");
    }

    @Test
    void metadataEndpointShouldPublishServiceProviderConfiguration() throws Exception {
        mockMvc.perform(get("/saml2/metadata/keycloak-saml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "entityID=\"autenticacao-lab-saml\""
                )))
                .andExpect(content().string(containsString(
                        "/login/saml2/sso/keycloak-saml"
                )));
    }

    @Test
    void authenticatedUserShouldReceiveOnlySelectedSamlIdentityData() throws Exception {
        var assertion = Saml2ResponseAssertion
                .withResponseValue("encoded-saml-response")
                .nameId("rafael")
                .attributes(Map.of(
                        "username", List.of("rafael"),
                        "firstName", List.of("Rafael"),
                        "lastName", List.of("Silva"),
                        "email", List.of("rafael@email.com")
                ))
                .build();
        var samlAuthentication = new Saml2AssertionAuthentication(
                assertion,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SAML2_USER")
                ),
                "keycloak-saml"
        );

        mockMvc.perform(get("/auth/saml/sobre")
                        .with(authentication(samlAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameId").value("rafael"))
                .andExpect(jsonPath("$.username").value("rafael"))
                .andExpect(jsonPath("$.name").value("Rafael Silva"))
                .andExpect(jsonPath("$.email").value("rafael@email.com"))
                .andExpect(jsonPath("$.registrationId").value("keycloak-saml"))
                .andExpect(jsonPath("$.authorities", containsInAnyOrder("ROLE_USER")))
                .andExpect(jsonPath("$.responseValue").doesNotExist())
                .andExpect(jsonPath("$.attributes").doesNotExist());
    }

    @Test
    void csrfEndpointShouldProvideTokenForLogout() throws Exception {
        mockMvc.perform(get("/auth/saml/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void localLogoutWithoutCsrfTokenShouldBeRejected() throws Exception {
        mockMvc.perform(post("/auth/saml/logout")
                        .with(authentication(samlAuthentication())))
                .andExpect(status().isForbidden());
    }

    @Test
    void localLogoutShouldClearOnlySamlContext() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT_SESSION", "session-context");

        mockMvc.perform(post("/auth/saml/logout")
                        .session(session)
                        .with(authentication(samlAuthentication()))
                        .with(csrf()))
                .andExpect(status().isFound());

        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT_SESSION"))
                .isEqualTo("session-context");
    }

    @Test
    void singleLogoutWithoutCsrfTokenShouldBeRejected() throws Exception {
        mockMvc.perform(post("/auth/saml/slo")
                        .with(authentication(samlAuthentication())))
                .andExpect(status().isForbidden());
    }

    @Test
    void loggedOutEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/auth/saml/logged-out"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        "http://localhost:5173/"
                ));
    }

    @Test
    void preflightShouldAllowConfiguredSamlOriginWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/auth/saml/logout")
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

    private Saml2AssertionAuthentication samlAuthentication() {
        var assertion = Saml2ResponseAssertion
                .withResponseValue("encoded-saml-response")
                .nameId("rafael")
                .build();

        return new Saml2AssertionAuthentication(
                assertion,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                "keycloak-saml"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SamlTestConfiguration {

        @Bean
        RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
            var registration = RelyingPartyRegistration
                    .withRegistrationId("keycloak-saml")
                    .entityId("autenticacao-lab-saml")
                    .assertionConsumerServiceLocation(
                            "{baseUrl}/login/saml2/sso/{registrationId}"
                    )
                    .singleLogoutServiceLocation("{baseUrl}/logout/saml2/slo")
                    .singleLogoutServiceResponseLocation("{baseUrl}/logout/saml2/slo")
                    .assertingPartyMetadata(assertingParty -> assertingParty
                            .entityId("https://idp.example")
                            .singleSignOnServiceLocation("https://idp.example/saml")
                            .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
                            .singleLogoutServiceLocation("https://idp.example/saml")
                            .singleLogoutServiceBinding(Saml2MessageBinding.POST)
                            .wantAuthnRequestsSigned(false)
                    )
                    .build();

            return new InMemoryRelyingPartyRegistrationRepository(registration);
        }
    }
}
