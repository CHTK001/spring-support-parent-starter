package com.chua.starter.ai.support.service;

/**
   * AI 访问守卫服务接口，负责 app钥匙 / Bearer 令牌 校验。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiAccessGuardService {

    /**
      * 是否启用 app钥匙 鉴权。
     *
     * @return 是否启用
     */
    boolean isAppKeyEnabled();

    /**
      * 校验 app钥匙 与 appsecret。
     *
     * @param appKey     应用 钥匙
     * @param appSecret  应用密钥
     * @param remoteAddr 客户端地址
     * @return 校验是否通过
     */
    boolean verifyAppKey(String appKey, String appSecret, String remoteAddr);

    /**
      * 校验 Bearer 令牌。
     *
     * @param auth       认证头
     * @param remoteAddr 客户端地址
     * @return 校验是否通过
     */
    boolean verifyBearerToken(String auth, String remoteAddr);
}
