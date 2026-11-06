// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.health;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 按渠道独立探针间隔的全局设置提供者 SPI（分组：channel_probe）。
 *
 * <p>渠道健康探针默认按 {@code health.channel_health_probe_interval_sec} 统一节奏跑，
 * 但各渠道的上游限流阈值、凭据寿命与免费额度刷新时间差别很大：免费额度渠道需要勤探
 * （额度用尽就该下线），付费渠道探得太勤只会撞限流。本分组把间隔下沉到「每个渠道一条」，
 * 由 {@link com.chua.starter.ai.support.manage.schedule.ChannelProbeWheelTask} 用时间轮
 * （{@code com.chua.common.support.task.timer.Timer}）按各自的周期独立调度。</p>
 *
 * <p>字段命名约定：{@code channel_probe_interval_sec.<channel>}，值为秒；缺失或 0 表示
 * 该渠道沿用全局间隔。已启用的渠道一栏由 {@code ChannelProbeWheelTask} 在启动时按
 * {@code sys_ai_provider.platform} 的实际取值补写（本 provider 只声明默认集，
 * 避免把尚未接入的渠道写进设置页）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("channel_probe")
public class ChannelProbeGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "channel_probe";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "渠道探针节奏";

    /**
     * 按渠道间隔的字段前缀
     */
    public static final String FIELD_PREFIX = "channel_probe_interval_sec.";

    /**
     * 时间轮开关字段
     */
    public static final String FIELD_ENABLED = "channel_probe_wheel_enabled";

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
                new SettingItem(FIELD_ENABLED, "true", "BOOLEAN",
                        "渠道独立探针时间轮开关：关闭后所有渠道统一走 health 分组的全局间隔", 1),
                new SettingItem(FIELD_PREFIX + "workbuddy", "0", "NUMBER",
                        "WorkBuddy 渠道探针间隔秒数（0 = 沿用全局间隔）", 2)
        );
    }
}
