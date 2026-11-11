package com.trionesdev.spring.security.client;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryClientRegistrationRepository implements ClientRegistrationRepository {
    private final Map<String, ClientRegistration> registrations = new ConcurrentHashMap<>();

    public InMemoryClientRegistrationRepository(Collection<ClientRegistration> registrations) {
        if (registrations != null) {
            registrations.forEach(this::register);
        }
    }

    /**
     * 以 map 的 key 作为 registrationId（适合从配置 {@code clients.xxx.client-id=...} 绑定）。
     */
    public InMemoryClientRegistrationRepository(Map<String, ClientRegistration> registrations) {
        if (registrations != null) {
            registrations.forEach((id, registration) -> {
                if (registration.getRegistrationId() == null) {
                    registration.setRegistrationId(id);
                }
                register(registration);
            });
        }
    }

    public void register(ClientRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(registration.getRegistrationId(), "registrationId");
        registrations.put(registration.getRegistrationId(), registration);
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrationId == null ? null : registrations.get(registrationId);
    }
}
