package com.trionesdev.spring.security.jwt;

import com.trionesdev.spring.security.AbstractFreeAuthenticationToken;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@EqualsAndHashCode(callSuper = true)
@Data
public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private String token;

    public JwtAuthenticationToken( ) {
        super(null);
    }
    public JwtAuthenticationToken(Collection<? extends GrantedAuthority> authorities) {
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
