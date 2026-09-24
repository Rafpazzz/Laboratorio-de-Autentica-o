package com.rafael.autenticacao.Authentication.saml.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SamlSecurityHandlersTest {

    private static final String SAML_ENDPOINT = "/login/saml2/sso/keycloak-saml";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationFailureHandlerShouldReturnUnauthorizedProblemDetail()
            throws Exception {
        var request = requestToSamlEndpoint();
        var response = new MockHttpServletResponse();
        var handler = new SamlAuthenticationFailureHandler(objectMapper);

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("Invalid assertion")
        );

        assertProblemDetail(
                response,
                HttpStatus.UNAUTHORIZED,
                "Resposta SAML invalida"
        );
    }

    @Test
    void authenticationEntryPointShouldReturnUnauthorizedProblemDetail() throws Exception {
        var request = requestToSamlEndpoint();
        var response = new MockHttpServletResponse();
        var handler = new SamlAuthenticationEntryPoint(objectMapper);

        handler.commence(
                request,
                response,
                new BadCredentialsException("Authentication required")
        );

        assertProblemDetail(
                response,
                HttpStatus.UNAUTHORIZED,
                "Autenticacao SAML necessaria"
        );
    }

    @Test
    void accessDeniedHandlerShouldReturnForbiddenProblemDetail() throws Exception {
        var request = requestToSamlEndpoint();
        var response = new MockHttpServletResponse();
        var handler = new SamlAccessDeniedHandler(objectMapper);

        handler.handle(
                request,
                response,
                new AccessDeniedException("Access denied")
        );

        assertProblemDetail(
                response,
                HttpStatus.FORBIDDEN,
                "Usuario SAML sem permissao para este recurso"
        );
    }

    private MockHttpServletRequest requestToSamlEndpoint() {
        var request = new MockHttpServletRequest();
        request.setRequestURI(SAML_ENDPOINT);

        return request;
    }

    private void assertProblemDetail(
            MockHttpServletResponse response,
            HttpStatus expectedStatus,
            String expectedDetail
    ) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body.get("title").asString())
                .isEqualTo(expectedStatus.getReasonPhrase());
        assertThat(body.get("status").asInt()).isEqualTo(expectedStatus.value());
        assertThat(body.get("detail").asString()).isEqualTo(expectedDetail);
        assertThat(body.get("instance").asString()).isEqualTo(SAML_ENDPOINT);
    }
}
