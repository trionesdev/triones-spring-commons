package com.trionesdev.spring.security.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 OkHttp 的 client_credentials token 请求实现（RFC 6749 4.4）。
 */
public class OkHttpClientCredentialsTokenResponseClient implements ClientCredentialsTokenResponseClient {
    private final OkHttpClient httpClient;
    private Clock clock = Clock.systemUTC();

    public OkHttpClientCredentialsTokenResponseClient() {
        this(new OkHttpClient());
    }

    public OkHttpClientCredentialsTokenResponseClient(OkHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthorizedClient getTokenResponse(ClientRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        if (StringUtils.isBlank(registration.getTokenUri())) {
            throw new AuthorizedClientException("tokenUri is required for client: " + registration.getRegistrationId());
        }
        Request request = buildRequest(registration);
        Instant issuedAt = clock.instant();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String content = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new AuthorizedClientException("Failed to obtain access token for client ["
                        + registration.getRegistrationId() + "], status: " + response.code() + ", body: " + content);
            }
            return parse(registration, content, issuedAt);
        } catch (IOException ex) {
            throw new AuthorizedClientException("Failed to obtain access token for client ["
                    + registration.getRegistrationId() + "]", ex);
        }
    }

    private Request buildRequest(ClientRegistration registration) {
        FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8)
                .add("grant_type", "client_credentials");
        if (registration.getScopes() != null && !registration.getScopes().isEmpty()) {
            form.add("scope", String.join(" ", registration.getScopes()));
        }
        Request.Builder builder = new Request.Builder()
                .url(registration.getTokenUri())
                .header("Accept", "application/json");
        if (registration.getClientAuthenticationMethod() == ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_POST) {
            form.add("client_id", registration.getClientId());
            if (registration.getClientSecret() != null) {
                form.add("client_secret", registration.getClientSecret());
            }
        } else {
            builder.header("Authorization", Credentials.basic(
                    registration.getClientId(), StringUtils.defaultString(registration.getClientSecret()),
                    StandardCharsets.UTF_8));
        }
        return builder.post(form.build()).build();
    }

    private AuthorizedClient parse(ClientRegistration registration, String content, Instant issuedAt) {
        JSONObject json;
        try {
            json = JSON.parseObject(content);
        } catch (Exception ex) {
            throw new AuthorizedClientException("Invalid token response for client ["
                    + registration.getRegistrationId() + "]: " + content, ex);
        }
        String accessToken = json == null ? null : json.getString("access_token");
        if (StringUtils.isBlank(accessToken)) {
            throw new AuthorizedClientException("Token response has no access_token for client ["
                    + registration.getRegistrationId() + "]: " + content);
        }
        String refreshToken = json.getString("refresh_token");
        Long expiresIn = json.getLong("expires_in");
        String scope = json.getString("scope");
        Set<String> scopes = StringUtils.isBlank(scope)
                ? registration.getScopes()
                : Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toCollection(LinkedHashSet::new));
        return AuthorizedClient.builder()
                .clientRegistration(registration)
                .accessToken(AccessToken.of(accessToken))
                .refreshToken(StringUtils.isBlank(refreshToken) ? null : RefreshToken.of(refreshToken))
                .tokenType(json.getString("token_type"))
                .issuedAt(issuedAt)
                .expiresAt(expiresIn == null ? null : issuedAt.plusSeconds(expiresIn))
                .scopes(scopes)
                .build();
    }
}
