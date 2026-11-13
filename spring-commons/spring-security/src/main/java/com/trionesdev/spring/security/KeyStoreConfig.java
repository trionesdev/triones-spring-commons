package com.trionesdev.spring.security;

import lombok.Data;
import org.springframework.core.io.Resource;

@Data
public class KeyStoreConfig {
    private Resource location;
    private String type = "PKCS12";
    private String password;
    private String alias;
    /**
     * 私钥密码，为空时使用 keystore 密码
     */
    private String keyPassword;
}
