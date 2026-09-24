package com.rafael.autenticacao.Authentication.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = FallbackSecurityConfigTest.UnclaimedController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                Saml2RelyingPartyAutoConfiguration.class
        }
)
@Import(FallbackSecurityConfig.class)
class FallbackSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void routeOutsideExplicitChainsShouldNotBeExposed() throws Exception {
        mockMvc.perform(get("/unclaimed"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Recurso nao encontrado"))
                .andExpect(jsonPath("$.instance").value("/unclaimed"));
    }

    @RestController
    static class UnclaimedController {

        @GetMapping("/unclaimed")
        String unclaimed() {
            return "should-not-be-exposed";
        }
    }
}
