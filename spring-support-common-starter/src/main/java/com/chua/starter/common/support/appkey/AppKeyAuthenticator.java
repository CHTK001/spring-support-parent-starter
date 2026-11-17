package com.chua.starter.common.support.appkey;

/**
 * 应用密钥（appKey）认证契约。
 *
 * <p>appKey 台账（{@code sys_ai_app_key}）属于 AI 模块，只有 AI 模块可以读写它。
 * 需要按 appKey 做本地鉴权的模块（例如 oauth-client 在单机部署 / 外部 o认证 服务
 * 不可达时的回退认证）只能依赖本契约，由 AI 模块提供实现；</p>
 *
 * <p><b>禁止</b>在调用方直接拼 SQL 查 AI 的 appKey 表：表名与列名一旦变化，
 * 调用方的 SQL 不会编译失败，只会在运行时静默失效。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface AppKeyAuthenticator {

    /**
     * appKey 台账是否可用（AI 模块已装配且可查询）。
     *
     * @return 可用返回 true
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 校验 appKey 并返回归属用户名。
     *
     * <p>校验口径由实现方决定（存在、启用、到期、次数 / Token 配额、IP 白名单），
     * 调用方不得再自行判定。</p>
     *
     * @param appKey appKey 值
     * @param ip     客户端 IP，可为空（为空时实现方不做白名单判定）
     * @return 归属用户名；校验不通过返回 {@code null}
     */
    String authenticate(String appKey, String ip);
}
