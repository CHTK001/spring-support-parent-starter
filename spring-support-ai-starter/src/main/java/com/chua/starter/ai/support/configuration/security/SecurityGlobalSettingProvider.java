package com.chua.starter.ai.support.configuration.security;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 网关安全全局设置提供者 SPI（分组：security）。
 *
 * <p>定义 April 2026 — Sub2API 参考模型的网关安全行为，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>分组内的每个字段都由阶段 3 的 {@code Gateway*SecurityFilter} 读走：开关关闭
 * 时对应拦截器放行，所有开关全部关闭（{@code security_enabled=false}）时链式动作消失，
 * 防护降级回 {@link com.chua.starter.ai.support.service.AiAccessGuardService} 的
 * Bearer 令牌鉴权/计费守卫。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi("security")
public class SecurityGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "security";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "网关安全";

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("security_enabled",
                        "true", "BOOLEAN",
                        "网关安全总开关：关闭时本级所有拦截器放行，防护降级回 Bearer 鉴权与计费守卫"),
                new SettingItem("security_gateway_path_prefixes",
                        "/v2/v1/,/v2/v1beta/,/v2/ai/", "STRING",
                        "生效路径前缀（逗号分隔）：默认 OpenAI 兼容网关 + AI 管理出口，开 /v2/v1/** 外的流量不会被拦截"),
                new SettingItem("security_rate_limit_enabled",
                        "true", "BOOLEAN",
                        "QPM 限流开关：关闭时不检查请求频率"),
                new SettingItem("security_rate_limit_qpm",
                        "60", "NUMBER",
                        "单 Key 每分钟请求数（AppKey 滑动窗口），-1 表示不限制"),
                new SettingItem("security_rate_limit_per_ip_qpm",
                        "200", "NUMBER",
                        "单 IP 每分钟请求数（IP 滑动窗口），-1 表示不限制"),
                new SettingItem("security_rate_limit_per_path_qpm",
                        "600", "NUMBER",
                        "单路径每分钟请求数（per-path 滑动窗口），-1 表示不限制"),
                new SettingItem("security_ip_enabled",
                        "true", "BOOLEAN",
                        "IP 黑白名单开关：关闭时只放行全局白名单（若未配则全部放行）"),
                new SettingItem("security_ip_global_blacklist",
                        "", "STRING",
                        "全局 IP 黑名单（逗号分隔，支持点分十进制前缀匹配，如 10.0.0. 匹配所有 10.0.0.x），非空"),
                new SettingItem("security_ip_global_whitelist",
                        "", "STRING",
                        "全局 IP 白名单（逗号分隔）：非空时仅白名单内的 IP 放行，黑名单不生效"),
                new SettingItem("security_connection_budget_enabled",
                        "true", "BOOLEAN",
                        "并发数开关：关闭时不使用在途并发预算"),
                new SettingItem("security_connection_budget_max",
                        "64", "NUMBER",
                        "单 Key 允许的最大在途并发数"),
                new SettingItem("security_body_limit_enabled",
                        "true", "BOOLEAN",
                        "请求体大小开关：关闭时不限制请求体长度"),
                new SettingItem("security_body_limit_max_bytes",
                        "8388608", "NUMBER",
                        "最大请求体字节数（默认 8MB；-1 表示不限制）"),
                new SettingItem("security_request_timeout_enabled",
                        "true", "BOOLEAN",
                        "逐请求超时开关：关闭时不做超时控制"),
                new SettingItem("security_request_timeout_millis",
                        "60000", "NUMBER",
                        "逐请求最大处理时间（毫秒）"),
                new SettingItem("security_health_paths",
                        "/healthz /ping /readyz", "STRING",
                        "健康检查路径（空格分隔）：零开销直回 200，不进入任何拦截器")
        );
    }
}
