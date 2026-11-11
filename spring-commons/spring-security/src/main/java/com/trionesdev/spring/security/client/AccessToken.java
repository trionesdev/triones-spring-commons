package com.trionesdev.spring.security.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccessToken {
    private String tokenValue;

    public static AccessToken of(String tokenValue) {
        return new AccessToken(tokenValue);
    }
}
