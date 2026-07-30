package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SQL 执行请求（支持分页）。
 *
 * @author CH
 */
@Data
@Schema(description = "SQL 执行请求（支持分页）")
public class PanelSqlExecuteRequest {

    /** 要执行的 SQL。 */
    @Schema(description = "要执行的 SQL")
    private String sql;

    /** 页码，从 1 开始；为 null 或 ≤ 0 时不分页。 */
    @Schema(description = "页码，从 1 开始；为 null 或 <= 0 时不分页")
    private Integer pageNum;

    /** 每页行数；为 null 或 ≤ 0 时不分页。 */
    @Schema(description = "每页行数；为 null 或 <= 0 时不分页")
    private Integer pageSize;
}
