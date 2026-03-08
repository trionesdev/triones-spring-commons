package com.trionesdev.spring.security.web;

import com.trionesdev.spring.security.AbstractFreeAuthenticationToken;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@EqualsAndHashCode(callSuper = true)
@Data
public class TokenAuthenticationToken extends AbstractFreeAuthenticationToken {
    private String token;

    public TokenAuthenticationToken( ) {
        super(null);
    }
    public TokenAuthenticationToken(Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }
}
