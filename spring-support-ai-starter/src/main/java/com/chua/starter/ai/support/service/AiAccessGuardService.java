package com.chua.starter.ai.support.service;

/**
 * AI 访问守卫服务接口，负责 appKey / Bearer 令牌 校验。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiAccessGuardService {

    /**
     * 是否启用 appKey 鉴权。
     *
     * @return 是否启用
     */
    boolean isAppKeyEnabled();

    /**
     * 校验 appKey 与 appsecret。
     *
     * @param appKey     应用 Key
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

    /**
     * 计费维度检查：余额/透支是否允许发起新的请求。
     *
     * <p>余额为负且不允许透支时，调用方（{@code BaseAiController}）回 HTTP 402 并提示充值；
     * 不拦截余额充足、退款/透支生效的 Key。</p>
     *
     * @param appKey appKey 值
     * @return 允许发起请求返回 true
     */
    boolean isBillable(String appKey);
}
