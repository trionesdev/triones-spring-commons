package com.trionesdev.spring.security;

import com.trionesdev.commons.context.actor.Actor;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@FunctionalInterface
public interface ActorAuthoritiesResolver {
    Collection<? extends GrantedAuthority> resolve(Actor actor);
}