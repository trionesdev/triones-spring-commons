package com.trionesdev.spring.security.jwt;

import com.trionesdev.commons.context.actor.Actor;

import java.util.Map;

@FunctionalInterface
public interface JwtActorResolver {
    Actor resolve(Map<String, Object> claims);
}