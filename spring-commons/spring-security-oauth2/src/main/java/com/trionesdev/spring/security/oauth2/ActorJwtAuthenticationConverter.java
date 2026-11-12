package com.trionesdev.spring.security.oauth2;

import com.trionesdev.commons.context.actor.Actor;
import com.trionesdev.spring.security.ActorAuthoritiesResolver;
import com.trionesdev.spring.security.jwt.JwtActorResolver;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.*;

/**
 * 把 OAuth2 access_token（已验签的 {@link Jwt}）转换为带 {@link Actor} 的认证结果。
 * <ul>
 *     <li>平台令牌：ACTOR_* 声明交给 {@link JwtActorResolver} 解析，与原 triones 过滤器一致；</li>
 *     <li>第三方 / 客户端凭证令牌（没有 ACTOR_*）：actorId 取 sub；</li>
 *     <li>client_id 放入 actor attributes；</li>
 *     <li>权限 = {@link ActorAuthoritiesResolver} 的结果 + 标准 scope 权限（SCOPE_xxx）。</li>
 * </ul>
 */
@RequiredArgsConstructor
public class ActorJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    public static final String ATTRIBUTE_CLIENT_ID = "client_id";

    private final JwtActorResolver jwtActorResolver;
    private final ActorAuthoritiesResolver actorAuthoritiesResolver;
    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var actor = Optional.ofNullable(jwtActorResolver.resolve(jwt.getClaims())).orElseGet(Actor::new);
        if (StringUtils.isBlank(actor.getActorId())) {
            actor.setActorId(jwt.getSubject());
        }
        var clientId = jwt.getClaimAsString(ATTRIBUTE_CLIENT_ID);
        if (StringUtils.isNotBlank(clientId)) {
            Map<String, Object> attributes = new HashMap<>(Optional.ofNullable(actor.getAttributes()).orElseGet(Map::of));
            attributes.put(ATTRIBUTE_CLIENT_ID, clientId);
            actor.setAttributes(attributes);
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Optional.ofNullable(actorAuthoritiesResolver.resolve(actor)).ifPresent(authorities::addAll);
        authorities.addAll(scopeAuthoritiesConverter.convert(jwt));
        return new ActorJwtAuthenticationToken(jwt, actor, authorities);
    }
}
