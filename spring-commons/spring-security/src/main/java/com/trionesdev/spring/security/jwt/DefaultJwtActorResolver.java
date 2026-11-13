package com.trionesdev.spring.security.jwt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.trionesdev.commons.context.actor.Actor;

import java.util.Map;
import java.util.Optional;

import static com.trionesdev.commons.core.jwt.ClaimsKeyConstant.*;
import static com.trionesdev.commons.core.jwt.ClaimsKeyConstant.ACTOR_MEMBER_ID;
import static com.trionesdev.commons.core.jwt.ClaimsKeyConstant.ACTOR_TENANT_ID;

public class DefaultJwtActorResolver implements JwtActorResolver {

    @Override
    public Actor resolve(Map<String, Object> claims) {
        if (claims == null) {
            return null;
        }
        String actorId = Optional.ofNullable(claims.get(ACTOR_ID)).map(String::valueOf).orElse(null);
        String userId = Optional.ofNullable(claims.get(ACTOR_USER_ID)).map(String::valueOf).orElse(null);
        String role = Optional.ofNullable(claims.get(ACTOR_ROLE)).map(String::valueOf).orElse(null);
        String tenantId = Optional.ofNullable(claims.get(ACTOR_TENANT_ID)).map(String::valueOf).orElse(null);
        String memberId = Optional.ofNullable(claims.get(ACTOR_MEMBER_ID)).map(String::valueOf).orElse(null);
        Map<String, Object> attributes = Optional.ofNullable(claims.get(ACTOR_ATTRIBUTES))
                .map(o -> JSON.parseObject(JSON.toJSONString(o), new TypeReference<Map<String, Object>>() {
                })).orElse(null);
        return Actor.builder()
                .actorId(actorId)
                .userId(userId)
                .role(role)
                .tenantId(tenantId)
                .memberId(memberId)
                .attributes(attributes)
                .build();

    }
}