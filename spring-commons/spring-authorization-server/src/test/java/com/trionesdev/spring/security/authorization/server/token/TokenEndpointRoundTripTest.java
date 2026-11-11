package com.trionesdev.spring.security.authorization.server.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.trionesdev.commons.core.jwt.ClaimsKeyConstant;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.security.auth.client.AuthorizedClientManager;
import com.trionesdev.spring.security.auth.client.ClientRegistration;
import com.trionesdev.spring.security.auth.client.InMemoryClientRegistrationRepository;
import com.trionesdev.spring.security.auth.client.jwks.JwkSetJwtVerifier;
import com.trionesdev.spring.security.authorization.server.jwks.JwksEndpointFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端：auth-client 的 {@link AuthorizedClientManager} 通过 HTTP 向本模块的 token 端点申请 token，
 * 资源服务器侧用 {@link JwkSetJwtVerifier} 从本模块的 JWKS 端点获取公钥校验。
 */
class TokenEndpointRoundTripTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws IOException {
        JwtFacade jwtFacade = new JwtFacade(JwtConfig.builder().algorithm(JWSAlgorithm.ES256).expiration(1800).build());
        RegisteredClientRepository clients = new InMemoryRegisteredClientRepository(
                RegisteredClient.withClientId("order-service")
                        .clientSecret(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("s3:cr+et%"))
                        .scopes("order.read", "order.write")
                        .build());
        ClientCredentialsTokenGenerator generator = new ClientCredentialsTokenGenerator(jwtFacade);
        generator.setIssuer("http://auth");
        Filter token = new TokenEndpointFilter("/auth/token", clients,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(), generator);
        Filter jwks = new JwksEndpointFilter(jwtFacade, "/auth/jwks", Duration.ofMinutes(5));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/token", exchange -> serve(exchange, token));
        server.createContext("/auth/jwks", exchange -> serve(exchange, jwks));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /**
     * 把 JDK HttpServer 的请求适配为 MockHttpServletRequest 交给过滤器处理
     */
    private static void serve(HttpExchange exchange, Filter filter) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(exchange.getRequestMethod(),
                exchange.getRequestURI().getPath());
        exchange.getRequestHeaders().forEach((name, values) -> values.forEach(v -> request.addHeader(name, v)));
        byte[] body = exchange.getRequestBody().readAllBytes();
        request.setContent(body);
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
            UriComponentsBuilder.newInstance().query(new String(body, StandardCharsets.UTF_8)).build().getQueryParams()
                    .forEach((name, values) -> values.forEach(v -> request.addParameter(
                            URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8))));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, new MockFilterChain());
        } catch (Exception e) {
            throw new IOException(e);
        }
        response.getHeaderNames().forEach(name -> exchange.getResponseHeaders().put(name, response.getHeaders(name)));
        byte[] out = response.getContentAsByteArray();
        exchange.sendResponseHeaders(response.getStatus(), out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            exchange.getResponseBody().write(out);
        }
        exchange.close();
    }

    private AuthorizedClientManager manager(ClientRegistration.ClientAuthenticationMethod method, Set<String> scopes) {
        return new AuthorizedClientManager(new InMemoryClientRegistrationRepository(List.of(ClientRegistration.builder()
                .registrationId("order")
                .clientId("order-service")
                .clientSecret("s3:cr+et%")
                .tokenUri(baseUrl + "/auth/token")
                .scopes(scopes)
                .clientAuthenticationMethod(method)
                .build())));
    }

    @Test
    void clientObtainsTokenAndResourceServerVerifiesViaJwks() {
        JwkSetJwtVerifier verifier = JwkSetJwtVerifier.withJwkSetUri(baseUrl + "/auth/jwks");
        for (ClientRegistration.ClientAuthenticationMethod method : ClientRegistration.ClientAuthenticationMethod.values()) {
            AuthorizedClientManager manager = manager(method, Set.of("order.read"));
            var authorized = manager.authorize(com.trionesdev.spring.security.auth.client.AuthorizeRequest.of("order"));
            assertEquals("Bearer", authorized.getTokenType());
            assertEquals(Set.of("order.read"), authorized.getScopes());
            assertNotNull(authorized.getExpiresAt());

            Map<String, Object> claims = verifier.verify(authorized.getAccessToken().getTokenValue());
            assertEquals("order-service", claims.get("sub"), method.name());
            assertEquals("order-service", claims.get(ClaimsKeyConstant.ACTOR_ID));
            assertEquals("order.read", claims.get("scope"));
            assertEquals("http://auth", claims.get("iss"));
        }
    }

    @Test
    void wrongSecretSurfacesAsClientException() {
        AuthorizedClientManager manager = new AuthorizedClientManager(new InMemoryClientRegistrationRepository(List.of(
                ClientRegistration.builder().registrationId("order").clientId("order-service").clientSecret("wrong")
                        .tokenUri(baseUrl + "/auth/token").build())));
        var ex = assertThrows(com.trionesdev.spring.security.auth.client.AuthorizedClientException.class,
                () -> manager.getAccessToken("order"));
        assertTrue(ex.getMessage().contains("401") && ex.getMessage().contains("invalid_client"), ex.getMessage());
    }
}
