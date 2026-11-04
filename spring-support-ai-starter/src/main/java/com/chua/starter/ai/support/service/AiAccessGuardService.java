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

    /**
     * 只读校验 Bearer 令牌：模型获取等<b>不计费</b>端点使用。
     *
     * <p>身份（存在 / 启用 / 到期 / 次数 / Token 总量 / IP 白名单）、分组限流与余额守卫
     * 一律照查，唯一差别是<b>不消耗「按次数」配额</b>。为什么必须分开：客户端会周期性
     * 探测 {@code /v1/models}（换模型、重连、SDK 初始化），把这些探测算进请求次数，
     * 一把 {@code maxUse=100} 的 Key 会在没真正对话的情况下被探完 —— 用户看到的是
     * 「刚买的额度自己没了」。</p>
     *
     * @param auth       认证头（{@code Bearer xxx}）
     * @param remoteAddr 客户端地址
     * @return 校验是否通过
     */
    boolean verifyBearerTokenReadOnly(String auth, String remoteAddr);
}
