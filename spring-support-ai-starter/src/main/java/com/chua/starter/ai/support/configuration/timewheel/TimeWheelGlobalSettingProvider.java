// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.timewheel;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 时间轮定时同步全局设置提供者 SPI（分组：timewheel）。
 *
 * <p>总开关开启后由 {@link com.chua.starter.ai.support.schedule.AiSyncTimeWheelTask}
 * 用时间轮（{@code com.chua.common.support.task.timer.Timer}）定时执行两类同步，
 * 并通过白名单把同步范围与提供商（provider id）或模型 id 关联：</p>
 * <ul>
 *   <li>模型指标同步：把已配置服务商的 {@code ChatClientIndicator} 指标定时落库，
 *       可按 provider id / model id 白名单缩小同步范围；</li>
 *   <li>AIHOT 资讯同步：定时抓取精选资讯落 {@code sys_ai_news}。</li>
 * </ul>
 *
 * <p>总开关 {@code timewheel_enabled} 关闭（默认）时两类同步保持原有节奏：
 * 指标走页面手动触发、资讯走查询侧懒同步。开启后按各自间隔定时执行，
 * 间隔与白名单在每次触发前重读，配置改动下一轮即生效（无需重启）；
 * 运行中把总开关拨回关闭则时间轮自然熄灭，重新开启需重启进程。</p>
 *
 * @author CH
 * @since 4.0.0.70
 */
@Spi("timewheel")
public class TimeWheelGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    public static final String GROUP = "timewheel";

    /**
     * 分组显示名称
     */
    public static final String GROUP_NAME = "时间轮";

    /**
     * 时间轮总开关字段
     */
    public static final String FIELD_ENABLED = "timewheel_enabled";

    /**
     * 指标同步开关字段
     */
    public static final String FIELD_METRICS_ENABLED = "timewheel_metrics_enabled";

    /**
     * 指标同步间隔字段（秒）
     */
    public static final String FIELD_METRICS_INTERVAL = "timewheel_metrics_interval_sec";

    /**
     * 指标同步 provider id 白名单字段（逗号分隔，留空 = 全部已配置服务商）
     */
    public static final String FIELD_METRICS_PROVIDERS = "timewheel_metrics_providers";

    /**
     * 指标同步 model id 白名单字段（逗号分隔，留空 = 全部模型）
     */
    public static final String FIELD_METRICS_MODEL_IDS = "timewheel_metrics_model_ids";

    /**
     * 资讯同步开关字段
     */
    public static final String FIELD_NEWS_ENABLED = "timewheel_news_enabled";

    /**
     * 资讯同步间隔字段（秒）
     */
    public static final String FIELD_NEWS_INTERVAL = "timewheel_news_interval_sec";

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
                new SettingItem(FIELD_ENABLED, "false", "BOOLEAN",
                        "时间轮总开关：开启后按下方间隔定时执行模型指标同步与 AIHOT 资讯同步", 1),
                new SettingItem(FIELD_METRICS_ENABLED, "true", "BOOLEAN",
                        "模型指标定时同步：按间隔把已配置服务商的指标落库，范围受下方白名单约束", 2),
                new SettingItem(FIELD_METRICS_INTERVAL, "1800", "NUMBER",
                        "指标同步间隔（秒），最小 60，默认 1800（30 分钟）", 3),
                new SettingItem(FIELD_METRICS_PROVIDERS, "", "STRING",
                        "指标 Provider 白名单：逗号分隔，同时匹配渠道 ID 与平台标识（platform），留空 = 全部已配置服务商", 4),
                new SettingItem(FIELD_METRICS_MODEL_IDS, "", "STRING",
                        "指标模型白名单：逗号分隔，写 openai/gpt-4o 或 gpt-4o 均可，留空 = 全部模型", 5),
                new SettingItem(FIELD_NEWS_ENABLED, "true", "BOOLEAN",
                        "AIHOT 资讯定时同步：按间隔抓取精选资讯（按 link 去重 upsert）", 6),
                new SettingItem(FIELD_NEWS_INTERVAL, "3600", "NUMBER",
                        "资讯同步间隔（秒），最小 60，默认 3600（1 小时）", 7)
        );
    }
}
