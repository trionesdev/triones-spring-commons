package com.trionesdev.spring.security.authorization.server.token;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRegisteredClientRepository implements RegisteredClientRepository {
    private final Map<String, RegisteredClient> clients = new ConcurrentHashMap<>();

    public InMemoryRegisteredClientRepository(RegisteredClient... clients) {
        this(Arrays.asList(clients));
    }

    public InMemoryRegisteredClientRepository(Collection<RegisteredClient> clients) {
        if (clients != null) {
            clients.forEach(this::register);
        }
    }

    /**
     * @throws IllegalArgumentException clientId 重复
     */
    public void register(RegisteredClient client) {
        if (clients.putIfAbsent(client.getClientId(), client) != null) {
            throw new IllegalArgumentException("Duplicate clientId: " + client.getClientId());
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return clientId == null ? null : clients.get(clientId);
    }
}
