package com.chua.starter.ai.support.market;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 技能市场条目（富字段）。
 *
 * <p>携带市场展示与直装所需的完整信息：图标、分类标签、ZIP/Git 安装地址等。
 * 由 {@link SkillMarketProvider} 提供，后端聚合后下发，避免前端直连第三方站点。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@Builder
public class SkillMarketEntry {

    /**
     * 技能唯一标识（通常为目录名）
     */
    private String id;

    /**
     * 展示名称
     */
    private String name;

    /**
     * 一句话描述
     */
    private String description;

    /**
     * 图标地址（可为空，前端用首字母兜底）
     */
    private String icon;

    /**
     * 分类标签
     */
    private List<String> labels;

    /**
     * ZIP 直装地址
     */
    private String zipUrl;

    /**
     * Git 仓库地址
     */
    private String gitUrl;

    /**
     * 详情页地址
     */
    private String detailUrl;

    /**
     * 版本号
     */
    private String version;

    /**
     * 是否推荐
     */
    private Boolean recommended;
}
