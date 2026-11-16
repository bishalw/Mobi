package com.mobi.auth.security;

import com.mobi.auth.config.SecurityConfig;
import com.mobi.auth.controller.TokenController;
import com.mobi.auth.controller.WellKnownController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** The real SecurityConfig and token endpoints, over MockMvc, without a database. */
@SpringJUnitWebConfig(AuthSecurityWebTest.Config.class)
class AuthSecurityWebTest {

    // Characters that must be form-encoded inside a Basic header (RFC 6749 2.3.1).
    private static final String CLIENT_SECRET = "s3cret:/+=%";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, TokenController.class, WellKnownController.class, Probe.class})
    static class Config {
        @Bean
        SigningKeys signingKeys() {
            return new SigningKeys(TokenIssuerTest.pkcs8Pem(), "");
        }

        @Bean
        TokenIssuer tokenIssuer(SigningKeys keys) {
            return new TokenIssuer(keys, "http://auth-service:8082", 900_000, 300);
        }

        @Bean
        ServiceClients serviceClients() {
            return new ServiceClients(Map.of("order-service", CLIENT_SECRET, "disabled-service", ""));
        }
    }

    @RestController
    static class Probe {
        @GetMapping("/auth/admin/ping")
        String admin() {
            return "admin";
        }

        @PostMapping("/auth/login")
        String login() {
            return "login";
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    TokenIssuer issuer;
    @Autowired
    JwtDecoder decoder;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void clientCredentialsGrantIssuesServiceToken() throws Exception {
        MvcResult result = mvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basic("order-service", CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andReturn();

        String token = result.getResponse().getContentAsString().replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
        assertThat(decoder.decode(token).getClaimAsString("role")).isEqualTo("SERVICE");
    }

    @Test
    void clientSecretPostIsAcceptedToo() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "order-service")
                        .param("client_secret", CLIENT_SECRET))
                .andExpect(status().isOk());
    }

    @Test
    void wrongSecretOrDisabledClientIsInvalidClient() throws Exception {
        for (String[] creds : new String[][]{{"order-service", "nope"}, {"disabled-service", ""}, {"unknown", "x"}}) {
            mvc.perform(post("/oauth2/token")
                            .header(HttpHeaders.AUTHORIZATION, basic(creds[0], creds[1]))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("grant_type=client_credentials"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_client"));
        }
    }

    @Test
    void otherGrantTypesAreUnsupported() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basic("order-service", CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=password&username=a&password=b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    @Test
    void jwksAndMetadataArePublic() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
        // Advertised by Spring Security 7 in WWW-Authenticate (RFC 9728), so it has to resolve.
        mvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isOk());
        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwks_uri").value("http://auth-service:8082/.well-known/jwks.json"));
    }

    @Test
    void adminEndpointsNeedAnAdminUserToken() throws Exception {
        mvc.perform(get("/auth/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")));
        mvc.perform(get("/auth/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(issuer.issueUserToken(2L, "bob", "USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/auth/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(issuer.issueServiceToken("order-service"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/auth/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(issuer.issueUserToken(1L, "root", "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void invalidTokenIsRejectedWithBearerError() throws Exception {
        mvc.perform(get("/auth/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer error=\"invalid_token\"")));
    }

    @Test
    void staleTokenDoesNotBreakLogin() throws Exception {
        mvc.perform(post("/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.garbage"))
                .andExpect(status().isOk());
    }

    private static String basic(String id, String secret) {
        String pair = URLEncoder.encode(id, StandardCharsets.UTF_8) + ":" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
