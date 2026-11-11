package com.trionesdev.spring.security.auth.client;

public interface ClientRegistrationRepository {

    /**
     * @return 对应的注册信息，不存在时返回 null
     */
    ClientRegistration findByRegistrationId(String registrationId);
}
