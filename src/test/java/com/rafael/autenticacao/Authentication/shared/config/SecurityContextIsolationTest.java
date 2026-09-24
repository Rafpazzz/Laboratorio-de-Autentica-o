package com.rafael.autenticacao.Authentication.shared.config;

import com.rafael.autenticacao.Authentication.oauth2.config.SecurityConfigByOAuth2;
import com.rafael.autenticacao.Authentication.saml.config.SecurityConfigBySaml;
import com.rafael.autenticacao.Authentication.session.config.SecurityConfigBySession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityContextIsolationTest {

    @Test
    void statefulAuthenticationModesShouldUseIndependentContexts() {
        SecurityContextRepository sessionRepository = new SecurityConfigBySession()
                .sessionSecurityContextRepository();
        SecurityContextRepository oauth2Repository = new SecurityConfigByOAuth2()
                .oauth2SecurityContextRepository();
        SecurityContextRepository samlRepository = new SecurityConfigBySaml()
                .samlSecurityContextRepository();
        var httpSession = new MockHttpSession();

        saveAuthentication(sessionRepository, httpSession, "session-user");
        saveAuthentication(oauth2Repository, httpSession, "oauth2-user");
        saveAuthentication(samlRepository, httpSession, "saml-user");

        assertThat(authenticationName(sessionRepository, httpSession))
                .isEqualTo("session-user");
        assertThat(authenticationName(oauth2Repository, httpSession))
                .isEqualTo("oauth2-user");
        assertThat(authenticationName(samlRepository, httpSession))
                .isEqualTo("saml-user");
    }

    private void saveAuthentication(
            SecurityContextRepository repository,
            MockHttpSession session,
            String principal
    ) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken(principal, null));

        repository.saveContext(
                context,
                requestWith(session),
                new MockHttpServletResponse()
        );
    }

    private String authenticationName(
            SecurityContextRepository repository,
            MockHttpSession session
    ) {
        return repository.loadDeferredContext(requestWith(session))
                .get()
                .getAuthentication()
                .getName();
    }

    private MockHttpServletRequest requestWith(MockHttpSession session) {
        var request = new MockHttpServletRequest();
        request.setSession(session);

        return request;
    }
}
