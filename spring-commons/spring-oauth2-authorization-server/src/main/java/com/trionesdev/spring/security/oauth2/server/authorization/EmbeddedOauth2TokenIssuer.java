package com.trionesdev.spring.security.oauth2.server.authorization;

import com.trionesdev.commons.core.jwt.JwtClaims;
import com.trionesdev.commons.core.jwt.JwtUtils;
import com.trionesdev.commons.exception.BusinessException;
import com.trionesdev.commons.exception.TrionesError;
import com.trionesdev.commons.model.AuthToken;
import com.trionesdev.spring.security.TokenIssuer;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 平台登录令牌签发：业务侧完成身份校验（账号密码、短信验证码、切换租户等）后，
 * 由授权服务器签发 access_token + refresh_token 并写入 oauth2_authorization；
 * refresh_token 通过 {@link #refresh(String)} 换新令牌。
 * <p>
 * 与授权服务器其它令牌一样：使用授权服务器的 RSA 密钥签名（/oauth2/jwks 可验签），
 * 并可通过 /oauth2/introspect 内省、/oauth2/revoke 撤销。
 * <p>
 * 令牌归属平台客户端（{@code oauth2.authorization-server.platform-client}），授权类型为
 * {@link #PLATFORM_GRANT_TYPE}；该类型不能在 /oauth2/token 使用，只能由本类在进程内签发。
 */
@AllArgsConstructor
public class EmbeddedOauth2TokenIssuer implements TokenIssuer {
    public static final AuthorizationGrantType PLATFORM_GRANT_TYPE =
            new AuthorizationGrantType("urn:triones:params:oauth:grant-type:platform-sign-in");
    /**
     * 平台 ACTOR_* 声明在授权记录中的属性名
     */
    public static final String ACTOR_CLAIMS_KEY = "triones.actor_claims";
    public static final TrionesError INVALID_REFRESH_TOKEN = TrionesError.builder()
            .code("INVALID_REFRESH_TOKEN").message("登录已失效，请重新登录").build();


    private final String clientId;

    private final AuthorizationServerSettings authorizationServerSettings;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<OAuth2Token> tokenGenerator;

    /**
     * 签发 access_token + refresh_token
     *
     * @param subject 令牌 sub（用户ID）
     * @param claims  平台 actor 信息（role、userId、tenantId、memberId 等）
     */
    public AuthToken issue(String subject, JwtClaims claims) {
        var registeredClient = findAppClient();

        Map<String, Object> actorClaims = new LinkedHashMap<>();
        if (Objects.nonNull(claims)) {
            JwtUtils.genClaims(claims).forEach((name, value) -> {
                if (Objects.nonNull(value)) {
                    actorClaims.put(name, value);
                }
            });
        }
        var principal = UsernamePasswordAuthenticationToken.authenticated(subject, null, AuthorityUtils.NO_AUTHORITIES);

        // actor 声明放在授权记录属性里传给 PlatformTokenCustomizer（JwtGenerator 不会透传令牌上下文的自定义 key），
        // 刷新时也从这里取，刷新后的 access_token 声明与登录时一致
        var authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(subject)
                .authorizationGrantType(PLATFORM_GRANT_TYPE)
                .authorizedScopes(registeredClient.getScopes())
                .attribute(Principal.class.getName(), principal)
                .attribute(ACTOR_CLAIMS_KEY, actorClaims);
        return issueTokens(registeredClient, authorizationBuilder, principal, PLATFORM_GRANT_TYPE, null);
    }

    /**
     * 用 refresh_token 换新的 access_token。
     * 默认轮换 refresh_token：旧的立即失效并返回新的；平台客户端配置 reuse-refresh-tokens=true 时沿用旧的。
     *
     * @throws BusinessException refresh_token 无效、过期、已撤销或不属于平台登录
     */
    public AuthToken refresh(String refreshTokenValue) {
        if (StringUtils.isBlank(refreshTokenValue)) {
            throw new BusinessException(INVALID_REFRESH_TOKEN);
        }
        var authorization = authorizationService.findByToken(refreshTokenValue, OAuth2TokenType.REFRESH_TOKEN);
        if (Objects.isNull(authorization)) {
            throw new BusinessException(INVALID_REFRESH_TOKEN);
        }
        var registeredClient = findAppClient();
        // 只接受平台登录签发的 refresh_token，第三方客户端走 /oauth2/token
        if (!registeredClient.getId().equals(authorization.getRegisteredClientId())
                || !PLATFORM_GRANT_TYPE.equals(authorization.getAuthorizationGrantType())) {
            throw new BusinessException(INVALID_REFRESH_TOKEN);
        }
        var refreshToken = authorization.getRefreshToken();
        if (Objects.isNull(refreshToken) || !refreshToken.isActive()) {
            throw new BusinessException(INVALID_REFRESH_TOKEN);
        }
        Authentication principal = authorization.getAttribute(Principal.class.getName());
        var reuse = registeredClient.getTokenSettings().isReuseRefreshTokens();
        return issueTokens(registeredClient, OAuth2Authorization.from(authorization), principal,
                AuthorizationGrantType.REFRESH_TOKEN, reuse ? refreshToken.getToken() : null);
    }

    /**
     * @param reusedRefreshToken 不为空时沿用该 refresh_token，否则生成新的（替换授权记录中的旧 refresh_token）
     */
    private AuthToken issueTokens(RegisteredClient registeredClient, OAuth2Authorization.Builder authorizationBuilder,
                                      Authentication principal, AuthorizationGrantType grantType,
                                      OAuth2RefreshToken reusedRefreshToken) {
        var scopes = registeredClient.getScopes();
        var contextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal)
                .authorization(authorizationBuilder.build())
                .authorizationServerContext(resolveAuthorizationServerContext())
                .authorizedScopes(scopes)
                .authorizationGrantType(grantType);

        var generated = tokenGenerator.generate(contextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
        if (!(generated instanceof Jwt jwt)) {
            throw new IllegalStateException("Platform access token must be a self-contained JWT");
        }
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, jwt.getTokenValue(),
                jwt.getIssuedAt(), jwt.getExpiresAt(), scopes);
        authorizationBuilder.token(accessToken,
                metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, jwt.getClaims()));

        var refreshToken = reusedRefreshToken;
        if (Objects.isNull(refreshToken)) {
            // 平台客户端需包含 refresh_token 授权类型，否则 OAuth2RefreshTokenGenerator 返回 null
            if (!(tokenGenerator.generate(contextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build())
                    instanceof OAuth2RefreshToken generatedRefreshToken)) {
                throw new IllegalStateException("Platform oauth2 client does not support refresh_token grant");
            }
            refreshToken = generatedRefreshToken;
            authorizationBuilder.refreshToken(refreshToken);
        }

        authorizationService.save(authorizationBuilder.build());
        return new AuthToken(accessToken.getTokenValue(), refreshToken.getTokenValue(),
                accessToken.getExpiresAt(), refreshToken.getExpiresAt());
    }

    private RegisteredClient findAppClient() {
        var registeredClient = registeredClientRepository.findByClientId(clientId);
        if (Objects.isNull(registeredClient)) {
            throw new IllegalStateException("Platform oauth2 client not registered: " + clientId);
        }
        return registeredClient;
    }

    /**
     * 在授权服务器端点之外签发时没有 AuthorizationServerContext，
     * 按配置的 issuer（未配置时取当前请求地址，与授权服务器默认行为一致）构造
     */
    private AuthorizationServerContext resolveAuthorizationServerContext() {
        var current = AuthorizationServerContextHolder.getContext();
        if (Objects.nonNull(current)) {
            return current;
        }
        var issuer = StringUtils.isNotBlank(authorizationServerSettings.getIssuer())
                ? authorizationServerSettings.getIssuer()
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return issuer;
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        };
    }

}
