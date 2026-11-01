package com.chua.starter.ai.support.configuration.health;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 渠道健康探针全局设置提供者 SPI（分组：health）。
 *
 * <p>定义渠道健康探针行为：探针总开关、探针间隔、上下线阈值、延迟警告门限、探针超时、
 * 限流状态码与回退探针策略。由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>{@link com.chua.starter.ai.support.manage.schedule.ChannelHealthProbeTask} 动态读取该分组，
 * {@link com.chua.starter.ai.support.service.ChannelHealthService} 审计探针结果。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Spi("health")
public class HealthGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "health";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "渠道健康";

    @Override
    /**
     * 分组
     * @return 返回值
     */
    public String group() {
        return GROUP;
    }

    @Override
    /**
     * 分组名称
     * @return 返回值
     */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /**
     * 获取Items
     * @return 返回值
     */
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("channel_health_probe_enabled", "true", "BOOLEAN", "渠道探针总开关：开启后定期对渠道发探针更新状态", 1),
                new SettingItem("channel_health_probe_interval_sec", "60", "NUMBER", "渠道探针间隔秒数，最小 30 秒，未配置默认 60", 2),
                new SettingItem("channel_health_failure_to_down", "3", "NUMBER", "连续失败次数达到阈值则判为 DOWN，路由层剔除", 3),
                new SettingItem("channel_health_down_to_recover", "2", "NUMBER", "连续成功次数达到阈值则从 DOWN 恢复为 ONLINE", 4),
                new SettingItem("channel_health_warning_latency_ms", "3000", "NUMBER", "探针延迟警告阈值（毫秒），超时无损但标记 WARNING", 5),
                new SettingItem("channel_health_probe_timeout_sec", "10", "NUMBER", "探针请求超时秒数（连接+读取），应小于探针间隔", 6),
                new SettingItem("channel_health_rate_limited_status", "429", "NUMBER", "命中上游限流的状态码（命中后标记 RATE_LIMITED，不计入连续失败）", 7),
                new SettingItem("channel_health_probe_root_fallback", "true", "BOOLEAN", "探针 404 时回退到源站根路径（/），区分端点错误与通道不可达", 8)
        );
    }
}
