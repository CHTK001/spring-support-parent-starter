package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 记忆维护结果：一轮维护各动作的条数，供接口回显与验收取证。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "记忆维护结果")
public class AiMemoryMaintenanceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "扫描的记忆条数")
    private Integer scanned;

    @Schema(description = "命中去重指纹被合并的条数")
    private Integer merged;

    @Schema(description = "强度被衰减改写的条数")
    private Integer decayed;

    @Schema(description = "因过期被置为 expired 的条数")
    private Integer expired;

    @Schema(description = "因强度过低被归档的条数")
    private Integer archived;

    @Schema(description = "因超出容量上限被淘汰的条数")
    private Integer trimmed;

    @Schema(description = "执行时间（毫秒）")
    private Long executedAt;
}
