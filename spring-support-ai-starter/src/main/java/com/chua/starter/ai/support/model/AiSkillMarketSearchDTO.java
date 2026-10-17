package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 技能市场搜索结果：跨市场源聚合的条目与各源真实状态。
 *
 * <p>关键词为空时即为榜单浏览，前端用它渲染「推荐」区。</p>
 *
 * @param enabled   市场总开关是否开启
 * @param keyword   本次生效的搜索关键词
 * @param items     聚合后的条目（按源内名次排列）
 * @param sources   各市场源的搜索状态
 * @param fetchedAt 抓取时间（毫秒）
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "技能市场搜索结果")
public record AiSkillMarketSearchDTO(
        @Schema(description = "市场总开关是否开启") Boolean enabled,
        @Schema(description = "本次生效的搜索关键词") String keyword,
        @Schema(description = "聚合后的条目（按源内名次排列）") List<AiSkillMarketItemDTO> items,
        @Schema(description = "各市场源的搜索状态") List<AiSkillMarketSourceDTO> sources,
        @Schema(description = "抓取时间（毫秒）") Long fetchedAt) implements Serializable {

    private static final long serialVersionUID = 1L;
}
