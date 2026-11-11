package com.trionesdev.spring.security.authorization.server.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.trionesdev.commons.core.jwt.ClaimsKeyConstant;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenEndpointFilterTest {
    private static final String PATH = "/auth/token";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private JwtFacade jwtFacade;
    private TokenEndpointFilter filter;

    @BeforeEach
    void setUp() {
        jwtFacade = new JwtFacade(JwtConfig.builder().algorithm(JWSAlgorithm.RS256).expiration(1800).build());
        RegisteredClientRepository repository = new InMemoryRegisteredClientRepository(
                RegisteredClient.withClientId("order-service")
                        .clientSecret(encoder.encode("s3cret"))
                        .scopes("order.read", "order.write")
                        .build(),
                RegisteredClient.withClientId("post-only")
                        .clientSecret("{noop}p@ss:+word%")
                        .clientAuthenticationMethods(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build());
        ClientCredentialsTokenGenerator generator = new ClientCredentialsTokenGenerator(jwtFacade);
        generator.setIssuer("https://auth.example.com");
        generator.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        filter = new TokenEndpointFilter(PATH, repository, encoder, generator);
    }

    private static MockHttpServletRequest tokenRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setContentType("application/x-www-form-urlencoded");
        request.addParameter("grant_type", "client_credentials");
        return request;
    }

    private static String basic(String id, String secret) {
        String raw = URLEncoder.encode(id, StandardCharsets.UTF_8) + ":" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static Map<String, Object> json(MockHttpServletResponse response) throws Exception {
        return JSONObjectUtils.parse(response.getContentAsString());
    }

    private static void assertError(MockHttpServletResponse response, int status, String error) throws Exception {
        assertEquals(status, response.getStatus(), response.getContentAsString());
        assertEquals(error, json(response).get("error"));
        assertNotNull(json(response).get("error_description"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void issuesTokenWithClientSecretBasic() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        request.addHeader("Authorization", basic("order-service", "s3cret"));
        MockHttpServletResponse response = execute(request);

        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertTrue(response.getContentType().startsWith("application/json"));
        Map<String, Object> body = json(response);
        assertEquals("Bearer", body.get("token_type"));
        assertEquals(1800L, ((Number) body.get("expires_in")).longValue());
        assertEquals("order.read order.write", body.get("scope"), "no scope requested -> all registered scopes");

        // 同一个 JwtFacade 可以校验；exp 已过（固定时钟在过去），这里只解码 claims
        JWTClaimsSet claims = com.nimbusds.jwt.SignedJWT.parse((String) body.get("access_token")).getJWTClaimsSet();
        assertEquals("order-service", claims.getSubject());
        assertEquals("order-service", claims.getClaim("client_id"));
        assertEquals("order-service", claims.getClaim(ClaimsKeyConstant.ACTOR_ID));
        assertEquals("https://auth.example.com", claims.getIssuer());
        assertEquals("order.read order.write", claims.getClaim("scope"));
        assertEquals(NOW, claims.getIssueTime().toInstant());
        assertEquals(NOW.plusSeconds(1800), claims.getExpirationTime().toInstant());
        assertNotNull(claims.getJWTID());
    }

    @Test
    void tokenIsVerifiableBySigningFacade() throws Exception {
        ClientCredentialsTokenGenerator generator = new ClientCredentialsTokenGenerator(jwtFacade);
        TokenEndpointFilter liveFilter = new TokenEndpointFilter(PATH, id -> RegisteredClient.withClientId(id)
                .clientSecret("{noop}x").build(), encoder, generator);
        MockHttpServletRequest request = tokenRequest();
        request.addHeader("Authorization", basic("svc", "x"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        liveFilter.doFilter(request, response, new MockFilterChain());
        String token = (String) json(response).get("access_token");
        assertEquals("svc", jwtFacade.parse(token).getSubject());
    }

    @Test
    void issuesTokenWithClientSecretPostAndSpecialCharacters() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        request.addParameter("client_id", "post-only");
        request.addParameter("client_secret", "p@ss:+word%");
        MockHttpServletResponse response = execute(request);
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals(300L, ((Number) json(response).get("expires_in")).longValue(), "per-client TTL");
        assertNull(json(response).get("scope"), "client without scopes");
    }

    @Test
    void basicCredentialsAreUrlDecoded() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        request.addHeader("Authorization", basic("post-only", "p@ss:+word%"));
        // post-only 不允许 basic：认证失败而不是解码错误
        assertError(execute(request), 401, "invalid_client");
    }

    @Test
    void requestedScopesMustBeRegistered() throws Exception {
        MockHttpServletRequest subset = tokenRequest();
        subset.addHeader("Authorization", basic("order-service", "s3cret"));
        subset.addParameter("scope", "order.read");
        assertEquals("order.read", json(execute(subset)).get("scope"));

        MockHttpServletRequest invalid = tokenRequest();
        invalid.addHeader("Authorization", basic("order-service", "s3cret"));
        invalid.addParameter("scope", "order.read admin");
        assertError(execute(invalid), 400, "invalid_scope");
    }

    @Test
    void invalidClientCredentials() throws Exception {
        MockHttpServletRequest wrongSecret = tokenRequest();
        wrongSecret.addHeader("Authorization", basic("order-service", "wrong"));
        MockHttpServletResponse response = execute(wrongSecret);
        assertError(response, 401, "invalid_client");
        assertEquals("Basic realm=\"oauth2\"", response.getHeader("WWW-Authenticate"));

        MockHttpServletRequest unknown = tokenRequest();
        unknown.addParameter("client_id", "nobody");
        unknown.addParameter("client_secret", "x");
        MockHttpServletResponse unknownResponse = execute(unknown);
        assertError(unknownResponse, 401, "invalid_client");
        assertEquals(json(response).get("error_description"), json(unknownResponse).get("error_description"),
                "unknown client and wrong secret are indistinguishable");
        assertNull(unknownResponse.getHeader("WWW-Authenticate"));

        assertError(execute(tokenRequest()), 401, "invalid_client");

        MockHttpServletRequest noSecret = tokenRequest();
        noSecret.addParameter("client_id", "order-service");
        assertError(execute(noSecret), 401, "invalid_client");
    }

    @Test
    void invalidRequests() throws Exception {
        MockHttpServletRequest noGrant = new MockHttpServletRequest("POST", PATH);
        noGrant.addHeader("Authorization", basic("order-service", "s3cret"));
        assertError(execute(noGrant), 400, "invalid_request");

        MockHttpServletRequest unsupported = new MockHttpServletRequest("POST", PATH);
        unsupported.addHeader("Authorization", basic("order-service", "s3cret"));
        unsupported.addParameter("grant_type", "password");
        assertError(execute(unsupported), 400, "unsupported_grant_type");

        MockHttpServletRequest repeated = tokenRequest();
        repeated.addHeader("Authorization", basic("order-service", "s3cret"));
        repeated.addParameter("scope", "order.read");
        repeated.addParameter("scope", "order.write");
        assertError(execute(repeated), 400, "invalid_request");

        MockHttpServletRequest twoMethods = tokenRequest();
        twoMethods.addHeader("Authorization", basic("order-service", "s3cret"));
        twoMethods.addParameter("client_secret", "s3cret");
        assertError(execute(twoMethods), 400, "invalid_request");

        MockHttpServletRequest secretInQuery = tokenRequest();
        secretInQuery.setQueryString("client_id=post-only&client_secret=x");
        secretInQuery.addParameter("client_id", "post-only");
        secretInQuery.addParameter("client_secret", "x");
        assertError(execute(secretInQuery), 400, "invalid_request");

        MockHttpServletRequest malformedBasic = tokenRequest();
        malformedBasic.addHeader("Authorization", "Basic !!!");
        assertError(execute(malformedBasic), 400, "invalid_request");
    }

    @Test
    void onlyPostOnEndpointOthersPassThrough() throws Exception {
        MockHttpServletResponse get = execute(new MockHttpServletRequest("GET", PATH));
        assertEquals(405, get.getStatus());
        assertEquals("POST", get.getHeader("Allow"));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/orders"), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest(), "other paths continue down the chain");

        MockHttpServletRequest withContext = tokenRequest();
        withContext.setContextPath("/app");
        withContext.setRequestURI("/app" + PATH);
        withContext.addHeader("Authorization", basic("order-service", "s3cret"));
        assertEquals(200, execute(withContext).getStatus());
    }

    @Test
    void claimsCustomizerCanAddAndOverrideClaims() throws Exception {
        ClientCredentialsTokenGenerator generator = new ClientCredentialsTokenGenerator(jwtFacade);
        generator.setClaimsCustomizer((claims, client, scopes) -> claims
                .claim(ClaimsKeyConstant.ACTOR_ROLE, "SERVICE")
                .claim(ClaimsKeyConstant.ACTOR_TENANT_ID, "t1"));
        RegisteredClient client = RegisteredClient.withClientId("svc").clientSecret("{noop}x").build();
        String token = generator.generate(client, java.util.Set.of()).tokenValue();
        JWTClaimsSet claims = jwtFacade.parse(token);
        assertEquals("SERVICE", claims.getClaim(ClaimsKeyConstant.ACTOR_ROLE));
        assertEquals("t1", claims.getClaim(ClaimsKeyConstant.ACTOR_TENANT_ID));
        assertNull(claims.getClaim("scope"));
    }

    @Test
    void registeredClientValidation() {
        assertThrows(IllegalArgumentException.class, () -> RegisteredClient.withClientId(" "));
        assertThrows(IllegalArgumentException.class, () -> RegisteredClient.withClientId("a").build());
        assertThrows(IllegalArgumentException.class, () -> RegisteredClient.withClientId("a").clientSecret("x")
                .scopes("bad scope").build());
        assertThrows(IllegalArgumentException.class, () -> RegisteredClient.withClientId("a").clientSecret("x")
                .accessTokenTimeToLive(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryRegisteredClientRepository(List.of(
                RegisteredClient.withClientId("a").clientSecret("x").build(),
                RegisteredClient.withClientId("a").clientSecret("y").build())));
        assertFalse(RegisteredClient.withClientId("a").clientSecret("{noop}hunter2").build().toString().contains("hunter2"));
    }
}
