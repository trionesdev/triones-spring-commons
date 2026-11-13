package com.trionesdev.spring.security.oauth2.server.authorization;

import com.nimbusds.jose.jwk.RSAKey;
import com.trionesdev.spring.security.KeyStoreConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class RASLoader {
    public static RSAKey loadRsaKey(KeyStoreConfig keyStoreProperties) throws Exception {
        var location = keyStoreProperties.getLocation();
        if (Objects.isNull(location)) {
            log.warn("key-store 未配置，使用随机生成的 RSA 密钥。"
                    + "重启后已签发的令牌全部失效，且多实例之间不一致，仅可用于开发环境。");
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        }
        var password = Objects.toString(keyStoreProperties.getPassword(), "").toCharArray();
        var keyPassword = StringUtils.isNotBlank(keyStoreProperties.getKeyPassword())
                ? keyStoreProperties.getKeyPassword().toCharArray() : password;
        var keyStore = KeyStore.getInstance(keyStoreProperties.getType());
        try (InputStream in = location.getInputStream()) {
            keyStore.load(in, password);
        }
        var rsaKey = RSAKey.load(keyStore, keyStoreProperties.getAlias(), keyPassword);
        if (Objects.isNull(rsaKey)) {
            throw new IllegalStateException("RSA key not found in key store, alias: " + keyStoreProperties.getAlias());
        }
        return rsaKey;
    }
}
