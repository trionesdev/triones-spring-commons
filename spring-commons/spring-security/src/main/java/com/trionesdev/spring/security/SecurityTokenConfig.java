package com.trionesdev.spring.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityTokenConfig {
    @Builder.Default
    private String headerKey = AUTHORIZATION;
    @Builder.Default
    private String queryParamKey = "token";
    @Builder.Default
    private int expires = 86400;
    @Builder.Default
    private int refreshExpires = 2592000;
    /**
     * token风格，只对非jwt时候生效
     */
    @Builder.Default
    private TokenStyle tokenStyle = TokenStyle.uuid;
    /**
     * 是否使用jwt
     */
    @Builder.Default
    private AuthType authType = AuthType.jwt;
    @Builder.Default
    private TokenType tokenType = TokenType.jwt;
    /**
     * jwt密钥
     */
    @Builder.Default
    private String secret = "trionesdev_secret";





}
