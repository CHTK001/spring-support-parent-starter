package com.chua.starter.ai.support.spi;

import java.util.List;
import java.util.Map;

/**
 * Skills 离线 Provider SPI。
 *
 * <p>每个本地/内置技能来源一个实现（如数据库内置技能、本地文件目录扫描），通过
 * {@code @Spi("source-key")} 注册。前端「Skills」设置页枚举全部 SPI 渲染来源
 * 配置列表（每个源一个开关），开关状态持久化在 {@code sys_ai_global_setting}
 * 的 {@code skills} 分组。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface SkillOfflineProvider {

    /**
     * 离线源名称（用于前端展示与开关 key）。
     *
     * @return 源名称
     */
    String name();

    /**
     * 列出该来源提供的技能列表。
     *
     * @return 技能列表（每项为可序列化 Map，含 code/name/description 等）
     */
    List<Map<String, Object>> listSkills();
}
