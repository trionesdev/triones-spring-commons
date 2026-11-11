package com.trionesdev.spring.security.client;

/**
 * 解析当前调用链上的用户 token，用于服务间调用时透传用户身份。
 */
@FunctionalInterface
public interface UserTokenResolver {

    /**
     * @return 当前用户的原始 token（不含 Bearer 前缀），没有用户信息时返回 null
     */
    String resolve();
}
