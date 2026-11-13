package com.trionesdev.spring.security.oauth2.server.authorization;

import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Map;
import java.util.Objects;

/**
 * 令牌声明定制：
 * <ul>
 *     <li>所有 access_token 写入 client_id；</li>
 *     <li>平台登录签发的 access_token 写入平台 ACTOR_* 声明（来自授权记录的属性），
 *     与原 JwtFacade 签发的令牌声明一致，平台和下游的 actor 解析无需改动。</li>
 * </ul>
 */
public class ActorTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    public static final String CLAIM_CLIENT_ID = "client_id";

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        var claims = context.getClaims();
        claims.claim(CLAIM_CLIENT_ID, context.getRegisteredClient().getClientId());

        if (Objects.isNull(context.getAuthorization())) {
            return;
        }
        Map<String, Object> actorClaims = context.getAuthorization().getAttribute(EmbeddedOauth2TokenIssuer.ACTOR_CLAIMS_KEY);
        if (Objects.nonNull(actorClaims)) {
            actorClaims.forEach((name, value) -> {
                if (Objects.nonNull(value)) {
                    claims.claim(name, value);
                }
            });
        }
    }
}
