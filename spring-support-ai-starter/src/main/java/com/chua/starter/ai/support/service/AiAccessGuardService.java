package com.chua.starter.ai.support.service;

/**
 * AI 访问守卫服务接口，负责 AppKey / Bearer Token 校验。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AiAccessGuardService {

    /**
     * 是否启用 AppKey 鉴权。
     *
     * @return 是否启用
     */
    boolean isAppKeyEnabled();

    /**
     * 校验 AppKey 与 AppSecret。
     *
     * @param appKey     应用 Key
     * @param appSecret  应用密钥
     * @param remoteAddr 客户端地址
     * @return 校验是否通过
     */
    boolean verifyAppKey(String appKey, String appSecret, String remoteAddr);

    /**
     * 校验 Bearer Token。
     *
     * @param auth       认证头
     * @param remoteAddr 客户端地址
     * @return 校验是否通过
     */
    boolean verifyBearerToken(String auth, String remoteAddr);
}
