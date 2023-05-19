package com.chua.starter.oauth.server.support.generation;

/**
 * 令牌生成器
 *
 * @author CH
 */
public interface TokenGeneration {
    /**
     * 生成令牌
     *
     * @param key key
     * @return 令牌
     */
    String generation(String key);
}
