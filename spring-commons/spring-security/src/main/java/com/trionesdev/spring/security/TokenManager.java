package com.trionesdev.spring.security;

public interface TokenManager  {
    Token createToken(String username);
}
