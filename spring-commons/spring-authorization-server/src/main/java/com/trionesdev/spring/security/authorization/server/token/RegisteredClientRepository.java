package com.trionesdev.spring.security.authorization.server.token;

/**
 * 按 clientId 查找已登记的客户端。客户端存放在数据库等处时自行实现。
 */
@FunctionalInterface
public interface RegisteredClientRepository {
    /**
     * @return 未登记时返回 null
     */
    RegisteredClient findByClientId(String clientId);
}
