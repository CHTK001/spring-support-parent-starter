package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 市场源的一次搜索状态，供前端如实展示哪些源空、哪些源失败。
 *
 * @param source  市场源标识
 * @param queried 是否参与本次搜索（被开关关闭时为 false）
 * @param count   该源返回条数
 * @param error   失败原因，成功时为空
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "技能市场源搜索状态")
public record AiSkillMarketSourceDTO(
        @Schema(description = "市场源标识") String source,
        @Schema(description = "是否参与本次搜索（被开关关闭时为 false）") Boolean queried,
        @Schema(description = "该源返回条数") Integer count,
        @Schema(description = "失败原因，成功时为空") String error) implements Serializable {

    private static final long serialVersionUID = 1L;
}
