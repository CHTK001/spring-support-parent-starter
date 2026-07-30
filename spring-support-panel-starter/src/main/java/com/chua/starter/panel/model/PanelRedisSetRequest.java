package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Redis SET 请求。
 *
 * @author CH
 */
@Data
@Schema(description = "Redis SET 请求")
public class PanelRedisSetRequest {
    /** Key 名称。 */
    @Schema(description = "Key 名称")
    private String key;
    /** 值。 */
    @Schema(description = "值")
    private String value;
    /** TTL 秒数，-1 表示不设置过期。 */
    @Schema(description = "TTL 秒数，-1 表示不设置过期")
    private long ttl = -1;
}
