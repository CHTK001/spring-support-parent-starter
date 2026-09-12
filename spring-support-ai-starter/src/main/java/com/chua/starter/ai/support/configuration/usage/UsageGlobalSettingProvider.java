package com.chua.starter.ai.support.configuration.usage;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
* 用量配置全局设置提供者 SPI（全局 分组）。
*
* <p>定义全局用量行为：用量开关、定时同步间隔、Provider SPI 开关（数据源白名单）、
* 后台时间轮开关。由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
* 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
*
* <p>{@link com.chua.starter.ai.support.schedule.UsageAggregationTask} 动态读取该分组：
* <ul>
*   <li>{@code enable_usage_analysis} —— 用量开关，关闭时跳过执行</li>
*   <li>{@code usage_report_interval} —— 定时同步时间（秒），驱动 Trigger / 时间轮节奏</li>
*   <li>{@code usage_sync_providers} —— Provider SPI 配置开关（逗号分隔白名单，留空 = 全部启用）</li>
*   <li>{@code usage_timewheel_enabled} —— 后台时间轮开关，开启后改用时间轮调度</li>
* </ul></p>
*
* @author CH
* @since 4.0.0.42
 */
@Spi("global")
public class UsageGlobalSettingProvider implements GlobalSettingProvider {

    /**
    * 配置分组标识
     */
    private static final String GROUP = "global";

    /**
    * 分组显示名称
     */
    private static final String GROUP_NAME = "用量配置";

    @Override
    /** 分组 */
    public String group() {
        return GROUP;
    }

    @Override
    /** 分组名称 */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /** 获取Items */
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("enable_usage_analysis", "true", "BOOLEAN", "用量开关：关闭后跳过定时同步与聚合", 1),
                new SettingItem("usage_report_interval", "300", "NUMBER", "定时同步时间（秒），最小 10 秒，未配置默认 300", 2),
                new SettingItem("usage_sync_providers", "", "STRING", "Provider SPI 配置开关：逗号分隔的数据源白名单，留空 = 全部启用", 3),
                new SettingItem("usage_timewheel_enabled", "false", "BOOLEAN", "后台时间轮：开启后用量同步改用时间轮调度", 4)
        );
    }
}
