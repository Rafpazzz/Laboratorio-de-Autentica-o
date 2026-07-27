package com.rafael.autenticacao.Authentication.session.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSecurityHandlersTest {

    private static final String PROTECTED_RESOURCE = "/usuarios";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationEntryPointShouldReturnUnauthorizedProblemDetail() throws Exception {
        MockHttpServletRequest request = requestToProtectedResource();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SessionAuthenticationEntryPoint entryPoint =
                new SessionAuthenticationEntryPoint(objectMapper);

        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Authentication required")
        );

        assertProblemDetail(
                response,
                HttpStatus.UNAUTHORIZED,
                "Autenticação necessaria"
        );
    }

    @Test
    void accessDeniedHandlerShouldReturnForbiddenProblemDetail() throws Exception {
        MockHttpServletRequest request = requestToProtectedResource();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SessionAccessDeniedHandler accessDeniedHandler =
                new SessionAccessDeniedHandler(objectMapper);

        accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("Access denied")
        );

        assertProblemDetail(
                response,
                HttpStatus.FORBIDDEN,
                "Acesso não autorizado"
        );
    }

    private MockHttpServletRequest requestToProtectedResource() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(PROTECTED_RESOURCE);
        return request;
    }

    private void assertProblemDetail(
            MockHttpServletResponse response,
            HttpStatus expectedStatus,
            String expectedDetail
    ) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body.get("title").asString()).isEqualTo(expectedStatus.getReasonPhrase());
        assertThat(body.get("status").asInt()).isEqualTo(expectedStatus.value());
        assertThat(body.get("detail").asString()).isEqualTo(expectedDetail);
        assertThat(body.get("instance").asString()).isEqualTo(PROTECTED_RESOURCE);
    }
}
