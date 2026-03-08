package com.trionesdev.spring.security.token;

public interface TokenManager  {
    SecurityToken createToken(TokenDefinition tokenDefinition);

    SecurityToken logout(String token);
}
