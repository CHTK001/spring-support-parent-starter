package com.chua.starter.ai.support.configuration.memory;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 记忆体全局设置提供者 SPI。
 *
 * <p>分组 {@code memory} 的每一项都被 {@code SysAiMemoryServiceImpl} 在运行期真实读取：
 * 开关控制自动抽取与自动注入，阈值控制召回条数、最低分、半衰期、归档与容量上限，
 * 改完下一轮对话即生效，不是只写不读的设置占位。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("memory")
public class MemoryGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 分组标识
     */
    public static final String GROUP = "memory";

    @Override
    /**
     * 分组标识。
     *
     * @return 分组名
     */
    public String group() {
        return GROUP;
    }

    @Override
    /**
     * 分组显示名。
     *
     * @return 设置页展示的分组名
     */
    public String groupName() {
        return "记忆体";
    }

    @Override
    /**
     * 分组内的设置项清单。
     *
     * @return 记忆体运行参数项
     */
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("memory_injection_enabled", "true", "BOOLEAN", "对话时自动召回并注入记忆", 1),
                new SettingItem("memory_auto_extract", "true", "BOOLEAN", "每轮对话结束后自动抽取记忆", 2),
                new SettingItem("memory_recall_limit", "8", "NUMBER", "单轮注入的记忆条数上限", 3),
                new SettingItem("memory_min_score", "0.2", "NUMBER", "注入的最低召回分数", 4),
                new SettingItem("memory_half_life_days", "30", "NUMBER", "强度半衰期（天，0=不衰减）", 5),
                new SettingItem("memory_archive_threshold", "0.05", "NUMBER", "低于该强度且已过静默期的记忆转归档", 6),
                new SettingItem("memory_quiet_days", "60", "NUMBER", "归档判定的静默天数", 7),
                new SettingItem("memory_max_per_user", "500", "NUMBER", "单用户活跃记忆上限（0=不限制）", 8),
                new SettingItem("memory_dedup", "true", "BOOLEAN", "按内容指纹合并同义记忆", 9),
                new SettingItem("memory_maintenance_enabled", "true", "BOOLEAN", "后台定期维护（衰减/过期/淘汰）", 10),
                new SettingItem("memory_maintenance_interval_minutes", "60", "NUMBER", "维护任务间隔（分钟）", 11),
                new SettingItem("memory_extract_model", "", "STRING", "模型抽取使用的模型名（留空走默认，且需可用模型）", 12)
        );
    }
}
