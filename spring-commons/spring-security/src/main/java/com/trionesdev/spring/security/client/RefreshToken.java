package com.trionesdev.spring.security.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {
    private String tokenValue;

    public static RefreshToken of(String tokenValue) {
        return new RefreshToken(tokenValue);
    }
}
