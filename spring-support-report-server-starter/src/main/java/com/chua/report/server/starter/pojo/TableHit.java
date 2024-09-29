package com.chua.report.server.starter.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * hits
 * @author CH
 * @since 2024/9/27
 */
@Data
@Schema(description = "hits")
public class TableHit {

    /**
     * name
     */
    @Schema(description = "表名")
    private String name;
    /**
     * fields
     */
    @Schema(description = "字段")
    private String[] fields;
}
