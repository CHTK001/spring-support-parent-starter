package com.chua.starter.ai.support.market;

import java.util.List;

/**
 * 技能市场来源 SPI。
 *
 * <p>实现方通过 JDK {@link java.util.ServiceLoader} 机制注册到
 * {@code META-INF/services/com.chua.starter.ai.support.market.SkillMarketProvider}，
 * 后端聚合所有已启用来源后统一对外提供，便于权限控制与来源扩展。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface SkillMarketProvider {

    /**
     * 来源唯一标识，同时作为设置开关后缀（{@code market_<code>}）。
     *
     * @return 来源标识
     */
    String code();

    /**
     * 是否默认启用。
     *
     * @return 默认是否启用
     */
    default boolean defaultEnabled() {
        return false;
    }

    /**
     * 列出/检索市场技能。
     *
     * @param keyword 关键词，可为空（空表示返回该来源的默认/精选集合）
     * @return 市场条目集合
     */
    List<SkillMarketEntry> list(String keyword);
}
