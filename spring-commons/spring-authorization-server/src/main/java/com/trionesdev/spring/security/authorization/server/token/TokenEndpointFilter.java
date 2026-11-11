package com.trionesdev.spring.security.authorization.server.token;

import com.nimbusds.jose.util.JSONObjectUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OAuth2 token 端点（RFC 6749 §3.2），目前支持 {@code client_credentials}（§4.4）。
 * <p>
 * 请求：{@code POST application/x-www-form-urlencoded}，参数 {@code grant_type=client_credentials}、可选 {@code scope}；
 * 客户端认证使用 {@code client_secret_basic} 或 {@code client_secret_post}。
 * <p>
 * 成功响应（§5.1）：{@code {"access_token":"...","token_type":"Bearer","expires_in":1800,"scope":"a b"}}，
 * 并带 {@code Cache-Control: no-store}。失败响应（§5.2）：{@code {"error":"invalid_client","error_description":"..."}}。
 * <p>
 * 通过 {@link TokenEndpointConfigurer} 加入 Spring Security 过滤器链使用，不要注册为 Spring Bean。
 */
public class TokenEndpointFilter extends OncePerRequestFilter {
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final String BASIC_PREFIX = "Basic ";

    private final String path;
    private final RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientCredentialsTokenGenerator tokenGenerator;

    public TokenEndpointFilter(String path, RegisteredClientRepository clientRepository, PasswordEncoder passwordEncoder,
                               ClientCredentialsTokenGenerator tokenGenerator) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("token endpoint path must start with '/': " + path);
        }
        this.path = path;
        this.clientRepository = Objects.requireNonNull(clientRepository, "clientRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
    }

    public String getPath() {
        return path;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matches(request);
    }

    boolean matches(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            response.setHeader(HttpHeaders.ALLOW, "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        try {
            RegisteredClient client = authenticateClient(request);
            String grantType = singleParameter(request, "grant_type");
            if (grantType == null) {
                throw TokenError.invalidRequest("grant_type is required");
            }
            if (!GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
                throw new TokenError("unsupported_grant_type", "Unsupported grant_type: " + grantType,
                        HttpServletResponse.SC_BAD_REQUEST);
            }
            Set<String> scopes = authorizeScopes(client, singleParameter(request, "scope"));
            ClientCredentialsTokenGenerator.IssuedToken token = tokenGenerator.generate(client, scopes);
            writeToken(response, token);
        } catch (TokenError error) {
            if (logger.isDebugEnabled()) {
                logger.debug("Token request rejected: " + error.error + " " + error.getMessage());
            }
            writeError(response, error);
        }
    }

    private RegisteredClient authenticateClient(HttpServletRequest request) {
        // client_secret 不能出现在 URL 中（RFC 6749 §2.3.1）
        String query = request.getQueryString();
        if (query != null && Arrays.stream(query.split("&")).anyMatch(p -> p.startsWith("client_secret="))) {
            throw TokenError.invalidRequest("client_secret must not be sent in the query string");
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String formClientId = singleParameter(request, "client_id");
        String formClientSecret = singleParameter(request, "client_secret");

        String clientId;
        String clientSecret;
        ClientAuthenticationMethod method;
        boolean basic = authorization != null && authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length());
        if (basic) {
            if (formClientSecret != null) {
                throw TokenError.invalidRequest("Multiple client authentication methods are not allowed");
            }
            String[] credentials = decodeBasic(authorization.substring(BASIC_PREFIX.length()).trim());
            clientId = credentials[0];
            clientSecret = credentials[1];
            if (formClientId != null && !formClientId.equals(clientId)) {
                throw TokenError.invalidRequest("client_id does not match the Authorization header");
            }
            method = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        } else if (formClientId != null) {
            clientId = formClientId;
            clientSecret = formClientSecret;
            method = ClientAuthenticationMethod.CLIENT_SECRET_POST;
        } else {
            throw TokenError.invalidClient("Client authentication required", false);
        }

        RegisteredClient client = clientRepository.findByClientId(clientId);
        // 不区分「客户端不存在」与「secret 错误」，避免枚举 clientId
        if (client == null || clientSecret == null || clientSecret.isEmpty()
                || !client.getClientAuthenticationMethods().contains(method)
                || !passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw TokenError.invalidClient("Client authentication failed", basic);
        }
        return client;
    }

    private static String[] decodeBasic(String encoded) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw TokenError.invalidRequest("Invalid Basic authentication token");
        }
        int colon = decoded.indexOf(':');
        if (colon <= 0) {
            throw TokenError.invalidRequest("Invalid Basic authentication token");
        }
        try {
            return new String[]{
                    URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8),
                    URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8)};
        } catch (IllegalArgumentException ex) {
            throw TokenError.invalidRequest("Invalid Basic authentication token");
        }
    }

    /**
     * 未传 scope 时授予客户端登记的全部 scope；传了则必须是登记范围的子集。
     */
    private static Set<String> authorizeScopes(RegisteredClient client, String scopeParameter) {
        if (scopeParameter == null || scopeParameter.isBlank()) {
            return new LinkedHashSet<>(client.getScopes());
        }
        Set<String> requested = new LinkedHashSet<>(Arrays.asList(scopeParameter.trim().split("\\s+")));
        for (String scope : requested) {
            if (!client.getScopes().contains(scope)) {
                throw new TokenError("invalid_scope", "Scope not allowed: " + scope, HttpServletResponse.SC_BAD_REQUEST);
            }
        }
        return requested;
    }

    /**
     * 参数不能重复（RFC 6749 §3.2）
     */
    private static String singleParameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length == 0) {
            return null;
        }
        if (values.length > 1) {
            throw TokenError.invalidRequest("Parameter must not be repeated: " + name);
        }
        String value = values[0];
        return value == null || value.isEmpty() ? null : value;
    }

    private static void writeToken(HttpServletResponse response, ClientCredentialsTokenGenerator.IssuedToken token)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", token.tokenValue());
        body.put("token_type", "Bearer");
        body.put("expires_in", token.expiresIn());
        if (!token.scopes().isEmpty()) {
            body.put("scope", String.join(" ", token.scopes()));
        }
        writeJson(response, HttpServletResponse.SC_OK, body);
    }

    private static void writeError(HttpServletResponse response, TokenError error) throws IOException {
        if (error.basicChallenge) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"oauth2\"");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error.error);
        body.put("error_description", error.getMessage());
        writeJson(response, error.status, body);
    }

    private static void writeJson(HttpServletResponse response, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = JSONObjectUtils.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private static final class TokenError extends RuntimeException {
        private final String error;
        private final int status;
        private final boolean basicChallenge;

        TokenError(String error, String description, int status) {
            this(error, description, status, false);
        }

        TokenError(String error, String description, int status, boolean basicChallenge) {
            super(description, null, false, false);
            this.error = error;
            this.status = status;
            this.basicChallenge = basicChallenge;
        }

        static TokenError invalidRequest(String description) {
            return new TokenError("invalid_request", description, HttpServletResponse.SC_BAD_REQUEST);
        }

        static TokenError invalidClient(String description, boolean basicChallenge) {
            return new TokenError("invalid_client", description, HttpServletResponse.SC_UNAUTHORIZED, basicChallenge);
        }
    }
}
