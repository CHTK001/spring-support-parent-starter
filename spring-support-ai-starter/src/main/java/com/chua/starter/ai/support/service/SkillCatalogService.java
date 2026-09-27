package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.market.SkillMarketEntry;

import java.util.List;
import java.util.Map;

/**
 * 技能来源清单服务。
 *
 * <p>聚合三类技能来源，供「安装技能」弹窗检索与系统设置开关：</p>
 * <ul>
 *   <li><b>本地来源</b>：{@code utils-support-datasearch-starter} 的 {@code SkillOfflineProvider}，
 *       扫描本机其它 AI 工具（Cursor、Trae、Claude Code 等）的技能目录，安装时直接复制目录；</li>
 *   <li><b>在线市场</b>：内置官网市场（ai.dingd.cn），提供分类与 ZIP 直装；</li>
 *   <li><b>在线聚合源</b>：{@code SkillOnlineProvider}（skills.sh、skillsmp 等），
 *       按关键词检索；其内容回源 GitHub，仅在网络可达时可装。</li>
 * </ul>
 *
 * <p>开关统一持久化在 {@code sys_ai_global_setting} 的 {@code skills} 分组。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface SkillCatalogService {

    /**
     * 获取全部来源配置（本地工具、在线市场、在线聚合源）及开关状态。
     *
     * @return 分组配置
     */
    Map<String, Object> getConfig();

    /**
     * 批量更新来源开关，键为来源键、值为开关状态。
     *
     * @param config 键值映射，可为 null
     */
    void updateConfig(Map<String, Boolean> config);

    /**
     * 聚合已启用本地工具的技能（按技能名去重，合并来源）。
     *
     * @param keyword 名称/描述过滤关键词，可空
     * @return 本地技能条目
     */
    List<Map<String, Object>> listLocal(String keyword);

    /**
     * 在已启用的在线聚合源中按关键词检索。
     *
     * @param keyword 关键词，聚合源通常要求非空
     * @return 市场清单条目
     */
    List<Map<String, Object>> searchAggregators(String keyword);

    /**
     * 聚合已启用市场来源（{@link com.chua.starter.ai.support.market.SkillMarketProvider} SPI）的技能。
     *
     * @param keyword 名称/描述过滤关键词，可空（空表示精选集合）
     * @return 市场条目（含图标、标签、ZIP/Git 安装地址）
     */
    List<SkillMarketEntry> listMarket(String keyword);

    /**
     * 列出内置（默认常用）技能：扫描 {@code {skillsRoot}/builtin}，
     * 这些技能随系统分发、默认启用，无需安装。
     *
     * @return 内置技能条目（id 为目录名，含友好名称、描述、分类、版本）
     */
    List<Map<String, Object>> listBuiltin();

    /**
     * 将本地工具的技能复制安装到本系统 {@code skills/user}。
     *
     * @param tool  来源工具键（provider name）
     * @param name  技能名
     * @param force 是否强制覆盖已存在技能
     * @return 安装结果
     */
    Map<String, Object> installLocal(String tool, String name, boolean force);
}
