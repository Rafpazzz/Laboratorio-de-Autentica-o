package com.rafael.autenticacao.Authentication.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SharedAuthenticationConfigTest {

    private final SharedAuthenticationConfig config = new SharedAuthenticationConfig();

    @Test
    void classShouldBeRegisteredAsSpringConfiguration() {
        assertThat(SharedAuthenticationConfig.class.isAnnotationPresent(Configuration.class)).isTrue();
    }

    @Test
    void passwordEncoderShouldUseBcrypt() {
        var passwordEncoder = config.passwordEncoder();

        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(passwordEncoder.matches("123456", passwordEncoder.encode("123456"))).isTrue();
    }

    @Test
    void authenticationManagerShouldComeFromSpringConfiguration() throws Exception {
        AuthenticationConfiguration authenticationConfiguration =
                mock(AuthenticationConfiguration.class);
        AuthenticationManager expectedManager = mock(AuthenticationManager.class);
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(expectedManager);

        AuthenticationManager authenticationManager =
                config.authenticationManager(authenticationConfiguration);

        assertThat(authenticationManager).isSameAs(expectedManager);
    }
}
