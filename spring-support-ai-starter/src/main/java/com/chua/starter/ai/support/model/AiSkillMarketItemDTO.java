package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 在线市场技能条目：由各市场源归一化后回给前端，字段全部来自源侧真实返回。
 *
 * @param id              市场内唯一标识（含源前缀）
 * @param source          市场源标识
 * @param rank            本次搜索内的名次
 * @param name            技能名称
 * @param description     技能描述，源侧无描述时为空
 * @param author          作者
 * @param url             技能来源地址，安装时按此地址下载
 * @param path            仓库内技能目录，供安装端定位目录
 * @param stars           Star 数，源侧无该指标时为 0
 * @param forks           Fork 数，源侧无该指标时为 0
 * @param installs        安装次数，源侧无该指标时为 0
 * @param contentLanguage 内容语言
 * @param installed       本地是否已存在同名技能
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "在线市场技能条目")
public record AiSkillMarketItemDTO(
        @Schema(description = "市场内唯一标识（含源前缀）") String id,
        @Schema(description = "市场源标识") String source,
        @Schema(description = "本次搜索内的名次") Integer rank,
        @Schema(description = "技能名称") String name,
        @Schema(description = "技能描述，源侧无描述时为空") String description,
        @Schema(description = "作者") String author,
        @Schema(description = "技能来源地址，安装时按此地址下载") String url,
        @Schema(description = "仓库内技能目录，供安装端定位目录") String path,
        @Schema(description = "Star 数，源侧无该指标时为 0") Long stars,
        @Schema(description = "Fork 数，源侧无该指标时为 0") Long forks,
        @Schema(description = "安装次数，源侧无该指标时为 0") Long installs,
        @Schema(description = "内容语言") String contentLanguage,
        @Schema(description = "本地是否已存在同名技能") Boolean installed) implements Serializable {

    private static final long serialVersionUID = 1L;
}
