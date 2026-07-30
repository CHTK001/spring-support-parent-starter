package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Redis Value 视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "Redis Value 视图")
public class PanelRedisValueView {
    /** Key 名称。 */
    @Schema(description = "Key 名称")
    private String key;
    /** 数据类型。 */
    @Schema(description = "数据类型")
    private String type;
    /** 过期时间（秒）。 */
    @Schema(description = "过期时间（秒）")
    private long ttl;
    /** STRING 类型的值。 */
    @Schema(description = "STRING 类型的值")
    private String stringValue;
    /** HASH 类型的值。 */
    @Schema(description = "HASH 类型的值")
    private Map<String, String> hashValue;
    /** LIST 类型的值。 */
    @Schema(description = "LIST 类型的值")
    private List<String> listValue;
    /** SET 类型的值。 */
    @Schema(description = "SET 类型的值")
    private List<String> setValue;
    /** ZSET 类型的值（member -> score）。 */
    @Schema(description = "ZSET 类型的值（member -> score）")
    private Map<String, Double> zsetValue;
}
