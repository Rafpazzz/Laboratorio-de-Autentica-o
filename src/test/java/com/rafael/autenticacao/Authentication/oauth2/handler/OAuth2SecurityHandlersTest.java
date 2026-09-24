package com.rafael.autenticacao.Authentication.oauth2.handler;

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

class OAuth2SecurityHandlersTest {

    private static final String ENDPOINT = "/auth/oauth2/sobre";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationEntryPointShouldReturnUnauthorizedProblemDetail() throws Exception {
        var response = new MockHttpServletResponse();
        var handler = new OAuth2AuthenticationEntryPoint(objectMapper);

        handler.commence(
                request(),
                response,
                new BadCredentialsException("Authentication required")
        );

        assertProblemDetail(
                response,
                HttpStatus.UNAUTHORIZED,
                "Autenticacao OAuth2 necessaria"
        );
    }

    @Test
    void accessDeniedHandlerShouldReturnForbiddenProblemDetail() throws Exception {
        var response = new MockHttpServletResponse();
        var handler = new OAuth2AccessDeniedHandler(objectMapper);

        handler.handle(
                request(),
                response,
                new AccessDeniedException("Access denied")
        );

        assertProblemDetail(
                response,
                HttpStatus.FORBIDDEN,
                "Usuario OAuth2 sem permissao para este recurso"
        );
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setRequestURI(ENDPOINT);

        return request;
    }

    private void assertProblemDetail(
            MockHttpServletResponse response,
            HttpStatus status,
            String detail
    ) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(status.value());
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body.get("title").asString()).isEqualTo(status.getReasonPhrase());
        assertThat(body.get("status").asInt()).isEqualTo(status.value());
        assertThat(body.get("detail").asString()).isEqualTo(detail);
        assertThat(body.get("instance").asString()).isEqualTo(ENDPOINT);
    }
}
