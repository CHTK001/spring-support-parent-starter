package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Redis Key 视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "Redis Key 视图")
public class PanelRedisKeyView {
    /** Key 名称。 */
    @Schema(description = "Key 名称")
    private String key;
    /** 数据类型。 */
    @Schema(description = "数据类型")
    private String type;
    /** 过期时间（秒）。 */
    @Schema(description = "过期时间（秒）")
    private long ttl;
    /** 大小。 */
    @Schema(description = "大小")
    private long size;
}
