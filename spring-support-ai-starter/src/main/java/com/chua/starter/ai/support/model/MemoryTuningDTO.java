package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 记忆体运行参数 —— 设置分组 {@code memory} 的内存镜像。
 *
 * <p>由服务层从 {@code sys_ai_global_setting} 读取后传给存储层，避免存储层直接依赖配置来源，
 * 也让一次对话内的多次召回看到同一份参数。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "记忆体运行参数")
public class MemoryTuningDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否在轮次结束后自动抽取记忆")
    private boolean autoExtract = true;

    @Schema(description = "是否在对话请求中自动注入记忆")
    private boolean injection = true;

    @Schema(description = "单轮注入条数上限")
    private int recallLimit = 8;

    @Schema(description = "注入的最低分数，低于该分且未置顶的记忆不进上下文")
    private double minScore = 0.2D;

    @Schema(description = "强度半衰期（天），0 表示不衰减")
    // [P3C A20 豁免]
    private double halfLifeDays = 30D;

    @Schema(description = "衰减后强度低于该值且超过 quietDays 的记忆转为归档")
    private double archiveThreshold = 0.05D;

    @Schema(description = "归档判定的静默天数")
    // [P3C A20 豁免]
    private int quietDays = 60;

    @Schema(description = "单用户活跃记忆上限，超出按分数淘汰非置顶记忆")
    // [P3C A20 豁免]
    private int maxPerUser = 500;

    @Schema(description = "是否按去重指纹合并同义记忆")
    private boolean dedup = true;
}
