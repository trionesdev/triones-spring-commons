package com.trionesdev.spring.security.oauth2;

import com.trionesdev.commons.context.actor.Actor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * OAuth2 资源服务器认证结果：在标准 {@link JwtAuthenticationToken} 基础上携带平台 {@link Actor}
 */
@Getter
public class ActorJwtAuthenticationToken extends JwtAuthenticationToken {
    private final Actor actor;

    public ActorJwtAuthenticationToken(Jwt jwt, Actor actor, Collection<? extends GrantedAuthority> authorities) {
        super(jwt, authorities, actor.getActorId());
        this.actor = actor;
    }
}
