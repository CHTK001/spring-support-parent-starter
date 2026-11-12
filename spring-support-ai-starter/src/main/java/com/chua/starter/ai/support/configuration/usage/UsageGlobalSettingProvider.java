// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
                new SettingItem("enable_usage_analysis", "true", "BOOLEAN", "用量分析开关：关闭后跳过定时同步与聚合", 1),
                new SettingItem("enable_usage_recorder", "true", "BOOLEAN", "会话用量写入开关：关闭后不记录每次调用到明细表", 2),
                // 2026-11-10：原 enable_connector_usage（连接器聊天用量开关）删除 ——
                // 全仓没有任何消费方读取它，开关拨了不生效。
                new SettingItem("usage_report_interval", "3600", "NUMBER", "定时聚合时间（秒），最小 60 秒，未配置默认 3600（1小时）", 4),
                new SettingItem("usage_timewheel_enabled", "false", "BOOLEAN", "后台时间轮：开启后用量同步改用时间轮调度", 5),
                new SettingItem("usage_sync_providers", "", "STRING", "Provider SPI 配置开关：逗号分隔的数据源白名单，留空 = 全部启用", 6),
                // 下面 5 项是**前端消费**配置：后端不读，由用量看板直接调设置接口取值
                // （UsageConfigSettings.vue / useUsageBoard.ts / usage/format.ts）。
                // 它们是真实生效的，只是生效点在浏览器侧，后端 grep 不到读取点。
                new SettingItem("usage_currency", "CNY", "STRING", "货币单位：CNY（人民币 ¥）/ USD（美元 $）/ EUR（欧元 €）", 7),
                new SettingItem("usage_token_scale", "auto", "STRING", "Token 显示量级：auto（自动 K/M/B 缩写）/ raw（完整数值）", 8),
                new SettingItem("usage_currency_auto_convert", "true", "BOOLEAN", "货币自动换算：开启后切换货币按下方汇率换算金额，关闭则仅切换符号", 9),
                new SettingItem("usage_rate_usd", "7.1", "NUMBER", "美元汇率：1 美元可兑换的人民币数量（CNY→USD 时金额除以该值）", 10),
                new SettingItem("usage_rate_eur", "7.8", "NUMBER", "欧元汇率：1 欧元可兑换的人民币数量（CNY→EUR 时金额除以该值）", 11)
        );
    }
}