package com.trionesdev.spring.security.token;

public interface TokenManager  {
    Token createToken(TokenDefinition tokenDefinition);

    Token logout(String token);
}
