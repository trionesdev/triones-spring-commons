package com.trionesdev.spring.security;

import com.trionesdev.commons.context.actor.Actor;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class DefaultActorAuthoritiesResolver implements ActorAuthoritiesResolver {
    @Override
    public Collection<? extends GrantedAuthority> resolve(Actor actor) {
        return List.of();
    }
}
