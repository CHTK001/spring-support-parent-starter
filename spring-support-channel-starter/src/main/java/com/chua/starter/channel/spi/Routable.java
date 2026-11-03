package com.chua.starter.channel.spi;

import com.chua.common.support.ai.chat.route.RouteTarget;

import java.util.Map;

/**
 * 可路由能力 SPI —— 把凭据转为 LLM 推理路由目标（仅 {@link ChannelKind#AI} 渠道适用）。
 *
 * <p>与 {@link FixedAccountProvider} 正交：AI 渠道需要它参与免费渠道路由，
 * 接码 / 收信渠道不需要。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface Routable {

    /**
     * 把凭据转为路由目标（供免费渠道聚合客户端按模型路由到本渠道上游）。
     *
     * <p>返回 {@code null} 表示本渠道不支持路由（无固定推理端点或需 WASM 签名等）。
     * 实现方应返回 {@link RouteTarget}，其中 {@code apiKey} 取自凭据的
     * {@code access_token}（session / device_token / JWT 等），{@code baseUrl}
     * 为本渠道的 OpenAI 兼容推理端点，认证字段按本渠道要求设置。</p>
     *
     * <p>多账号 failover：{@code collectChannelRoutes} 会为同一渠道的多个连接
     * 各自调用本方法，再把返回的 {@code apiKey} 聚合到同一个 {@code RouteTarget.apiKeys}
     * 列表，触发 429 限流时自动切换到下一个账号。</p>
     *
     * @param credentials 连接凭据（含 {@code access_token} 等）
     * @return 路由目标；不支持路由时返回 {@code null}
     */
    RouteTarget toRouteTarget(Map<String, Object> credentials);
}
