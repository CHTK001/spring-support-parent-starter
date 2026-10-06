package com.chua.starter.ai.support.manage.api.market;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智能体市场源扩展点。
 *
 * <p>对标技能的 {@code SkillMarketSourceProvider}：一个来源代表一处可分发的智能体仓库，
 * 通过 SPI 注册（{@code META-INF/extensions/} + {@code @Spi}）后即可被「安装智能体」
 * 页枚举与检索。</p>
 *
 * <p>实现应为无状态、可直接实例化（由 SPI 加载，不参与 Spring 注入）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AgentMarketSourceProvider {

    /**
     * 来源标识（SPI 名，唯一）。
     *
     * @return 来源标识
     */
    String source();

    /**
     * 展示名（前端下拉显示）。
     *
     * @return 展示名
     */
    String displayName();

    /**
     * 来源地址（远程仓库地址；本地来源可返回本地路径说明）。
     *
     * @return 来源地址
     */
    String url();

    /**
     * 是否默认启用。
     *
     * @return 默认启用返回 true
     */
    default boolean defaultEnabled() {
        return true;
    }

    /**
     * 按关键词检索可安装的智能体包。
     *
     * @param keyword 关键词，可为空（返回全部）
     * @param limit   条数上限
     * @return 条目列表，每项建议包含 name/description/model/source/url/path
     */
    default List<Map<String, Object>> search(String keyword, int limit) {
        return Collections.emptyList();
    }
}
