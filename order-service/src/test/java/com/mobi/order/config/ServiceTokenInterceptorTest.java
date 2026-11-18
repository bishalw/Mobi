package com.mobi.order.config;

import com.sun.net.httpserver.HttpServer;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FeignConfig's service-token interceptor against a stand-in for auth-service's /oauth2/token. */
class ServiceTokenInterceptorTest {

    private HttpServer tokenEndpoint;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private volatile int status = 200;

    @BeforeEach
    void start() throws Exception {
        tokenEndpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tokenEndpoint.createContext("/oauth2/token", exchange -> {
            int n = calls.incrementAndGet();
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = (status == 200
                    ? "{\"access_token\":\"svc-token-" + n + "\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    : "{\"error\":\"invalid_client\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        tokenEndpoint.start();
    }

    @AfterEach
    void stop() {
        tokenEndpoint.stop(0);
    }

    @Test
    void sendsOwnServiceTokenAndReusesItUntilExpiry() {
        RequestInterceptor interceptor = interceptor();

        RequestTemplate first = new RequestTemplate();
        interceptor.apply(first);
        RequestTemplate second = new RequestTemplate();
        interceptor.apply(second);

        assertThat(first.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer svc-token-1");
        assertThat(second.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer svc-token-1");
        assertThat(calls).hasValue(1);
        assertThat(bodies.get(0)).contains("grant_type=client_credentials");
        String expectedBasic = "Basic " + Base64.getEncoder().encodeToString("order-service:s3cret".getBytes(StandardCharsets.UTF_8));
        assertThat(authorizationHeaders.get(0)).isEqualTo(expectedBasic);
    }

    @Test
    void rejectedClientSurfacesAsOAuth2AuthorizationException() {
        status = 401;

        assertThatThrownBy(() -> interceptor().apply(new RequestTemplate()))
                .isInstanceOf(OAuth2AuthorizationException.class);
    }

    private RequestInterceptor interceptor() {
        ClientRegistration registration = ClientRegistration.withRegistrationId(FeignConfig.SERVICE_CLIENT_REGISTRATION)
                .clientId("order-service")
                .clientSecret("s3cret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .tokenUri("http://127.0.0.1:" + tokenEndpoint.getAddress().getPort() + "/oauth2/token")
                .build();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        FeignConfig config = new FeignConfig();
        return config.serviceTokenInterceptor(
                config.serviceTokenManager(registrations, new InMemoryOAuth2AuthorizedClientService(registrations)));
    }
}
