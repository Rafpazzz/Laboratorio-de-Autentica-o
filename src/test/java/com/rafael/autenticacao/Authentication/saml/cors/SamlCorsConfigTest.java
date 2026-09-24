package com.rafael.autenticacao.Authentication.saml.cors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SamlCorsConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private final DefaultCorsProcessor corsProcessor = new DefaultCorsProcessor();

    private CorsConfigurationSource configurationSource;

    @BeforeEach
    void setUp() {
        configurationSource = new SamlCorsConfig()
                .samlCorsConfigurationSource(ALLOWED_ORIGIN);
    }

    @ParameterizedTest
    @CsvSource({
            "/auth/saml/sobre, GET",
            "/auth/saml/logout, POST",
            "/auth/saml/slo, POST"
    })
    void preflightShouldAllowConfiguredOriginAndRequiredHeaders(
            String requestUri,
            String requestedMethod
    ) throws Exception {
        MockHttpServletRequest request = preflightRequest(
                requestUri,
                ALLOWED_ORIGIN,
                requestedMethod
        );
        var response = new MockHttpServletResponse();

        boolean processed = corsProcessor.processRequest(
                corsConfiguration(request),
                request,
                response
        );

        assertThat(processed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains(requestedMethod);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase(HttpHeaders.CONTENT_TYPE)
                .containsIgnoringCase("X-CSRF-TOKEN");
    }

    @Test
    void preflightShouldRejectUnknownOrigin() throws Exception {
        MockHttpServletRequest request = preflightRequest(
                "/auth/saml/sobre",
                "https://origem-nao-permitida.example",
                "GET"
        );
        var response = new MockHttpServletResponse();

        boolean processed = corsProcessor.processRequest(
                corsConfiguration(request),
                request,
                response
        );

        assertThat(processed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void configurationShouldNotApplyOutsideSamlRoutes() {
        var request = new MockHttpServletRequest("OPTIONS", "/auth/oauth2/sobre");

        assertThat(configurationSource.getCorsConfiguration(request)).isNull();
    }

    private MockHttpServletRequest preflightRequest(
            String requestUri,
            String origin,
            String requestedMethod
    ) {
        var request = new MockHttpServletRequest("OPTIONS", requestUri);
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod);
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Content-Type, X-CSRF-TOKEN"
        );

        return request;
    }

    private CorsConfiguration corsConfiguration(MockHttpServletRequest request) {
        CorsConfiguration configuration = configurationSource.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();

        return configuration;
    }
}
