package com.chua.starter.ai.support.market;

import java.util.List;

/**
 * 技能市场条目（富字段）。
 *
 * <p>携带市场展示与直装所需的完整信息：图标、分类标签、ZIP/Git 安装地址等。
 * 由 {@link SkillMarketProvider} 提供，后端聚合后下发，避免前端直连第三方站点。</p>
 *
 * @param id          技能唯一标识（通常为目录名）
 * @param name        展示名称
 * @param description 一句话描述
 * @param icon        图标地址（可为空，前端用首字母兜底）
 * @param labels      分类标签
 * @param zipUrl      ZIP 直装地址
 * @param gitUrl      Git 仓库地址
 * @param detailUrl   详情页地址
 * @param version     版本号
 * @param recommended 是否推荐
 * @since 4.0.0.43
 * @author CH
 */
public record SkillMarketEntry(
        String id,
        String name,
        String description,
        String icon,
        List<String> labels,
        String zipUrl,
        String gitUrl,
        String detailUrl,
        String version,
        Boolean recommended) {
}
