package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 记忆维护结果：一轮维护各动作的条数，供接口回显与验收取证。
 *
 * @param scanned    扫描的记忆条数
 * @param merged     命中去重指纹被合并的条数
 * @param decayed    强度被衰减改写的条数
 * @param expired    因过期被置为 expired 的条数
 * @param archived   因强度过低被归档的条数
 * @param trimmed    因超出容量上限被淘汰的条数
 * @param executedAt 执行时间（毫秒）
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "记忆维护结果")
public record AiMemoryMaintenanceDTO(
        @Schema(description = "扫描的记忆条数") Integer scanned,
        @Schema(description = "命中去重指纹被合并的条数") Integer merged,
        @Schema(description = "强度被衰减改写的条数") Integer decayed,
        @Schema(description = "因过期被置为 expired 的条数") Integer expired,
        @Schema(description = "因强度过低被归档的条数") Integer archived,
        @Schema(description = "因超出容量上限被淘汰的条数") Integer trimmed,
        @Schema(description = "执行时间（毫秒）") Long executedAt) implements Serializable {

    private static final long serialVersionUID = 1L;
}
