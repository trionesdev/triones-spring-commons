package com.trionesdev.spring.security.auth.client;

import com.trionesdev.spring.security.auth.client.AuthorizedClient;
import com.trionesdev.spring.security.auth.client.AuthorizedClientException;
import com.trionesdev.spring.security.auth.client.ClientRegistration;
import com.trionesdev.spring.security.auth.client.OkHttpClientCredentialsTokenResponseClient;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OkHttpClientCredentialsTokenResponseClientTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final AtomicReference<Request> captured = new AtomicReference<>();

    private OkHttpClientCredentialsTokenResponseClient client(int status, String body) {
        OkHttpClient http = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    captured.set(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message("fake")
                            .body(ResponseBody.create(body, MediaType.get("application/json")))
                            .build();
                })
                .build();
        OkHttpClientCredentialsTokenResponseClient tokenClient = new OkHttpClientCredentialsTokenResponseClient(http);
        tokenClient.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        return tokenClient;
    }

    private ClientRegistration registration(ClientRegistration.ClientAuthenticationMethod method) {
        return ClientRegistration.builder()
                .registrationId("svc")
                .clientId("my-client")
                .clientSecret("my-secret")
                .tokenUri("http://auth.local/oauth2/token")
                .scopes(new LinkedHashSet<>(List.of("read", "write")))
                .clientAuthenticationMethod(method)
                .build();
    }

    private static String bodyOf(Request request) throws Exception {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    @Test
    void basicAuthRequestAndResponseParsing() throws Exception {
        AuthorizedClient result = client(200,
                "{\"access_token\":\"abc\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"read\"}")
                .getTokenResponse(registration(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC));

        Request request = captured.get();
        assertEquals("POST", request.method());
        assertEquals("http://auth.local/oauth2/token", request.url().toString());
        assertEquals(Credentials.basic("my-client", "my-secret"), request.header("Authorization"));
        String body = bodyOf(request);
        assertTrue(body.contains("grant_type=client_credentials"));
        assertTrue(body.contains("scope=read%20write"));
        assertFalse(body.contains("client_secret"));

        assertEquals("abc", result.getAccessToken().getTokenValue());
        assertNull(result.getRefreshToken());
        assertEquals("Bearer", result.getTokenType());
        assertEquals(NOW, result.getIssuedAt());
        assertEquals(NOW.plusSeconds(3600), result.getExpiresAt());
        assertEquals(Set.of("read"), result.getScopes());
        assertEquals("svc", result.getRegistrationId());
    }

    @Test
    void clientSecretPostPutsCredentialsInForm() throws Exception {
        AuthorizedClient result = client(200, "{\"access_token\":\"abc\",\"refresh_token\":\"r1\"}")
                .getTokenResponse(registration(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_POST));

        Request request = captured.get();
        assertNull(request.header("Authorization"));
        String body = bodyOf(request);
        assertTrue(body.contains("client_id=my-client"));
        assertTrue(body.contains("client_secret=my-secret"));

        assertNull(result.getExpiresAt());
        assertEquals("r1", result.getRefreshToken().getTokenValue());
        assertEquals(Set.of("read", "write"), result.getScopes());
    }

    @Test
    void errorStatusThrows() {
        OkHttpClientCredentialsTokenResponseClient tokenClient = client(401, "{\"error\":\"invalid_client\"}");
        AuthorizedClientException ex = assertThrows(AuthorizedClientException.class,
                () -> tokenClient.getTokenResponse(registration(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void missingAccessTokenThrows() {
        OkHttpClientCredentialsTokenResponseClient tokenClient = client(200, "{\"token_type\":\"Bearer\"}");
        assertThrows(AuthorizedClientException.class,
                () -> tokenClient.getTokenResponse(registration(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)));
    }

    @Test
    void nonJsonResponseThrows() {
        OkHttpClientCredentialsTokenResponseClient tokenClient = client(200, "<html>oops</html>");
        assertThrows(AuthorizedClientException.class,
                () -> tokenClient.getTokenResponse(registration(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)));
    }

    @Test
    void missingTokenUriThrows() {
        ClientRegistration registration = registration(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        registration.setTokenUri(null);
        assertThrows(AuthorizedClientException.class, () -> client(200, "{}").getTokenResponse(registration));
    }
}
