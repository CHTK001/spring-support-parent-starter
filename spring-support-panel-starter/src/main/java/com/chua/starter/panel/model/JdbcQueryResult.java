package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * JDBC 查询结果。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "JDBC 查询结果")
public class JdbcQueryResult {

    /** 列名列表。 */
    @Schema(description = "列名列表")
    private List<String> columns;
    /** 行数据列表。 */
    @Schema(description = "行数据列表")
    private List<Map<String, Object>> rows;
    /** 影响行数。 */
    @Schema(description = "影响行数")
    private long affectedRows;
    /** 耗时（毫秒）。 */
    @Schema(description = "耗时（毫秒）")
    private long elapsedMillis;
    /** 是否为查询语句。 */
    @Schema(description = "是否为查询语句")
    private boolean query;

    /** 总行数（分页时有效，-1 表示未统计）。 */
    @Builder.Default
    @Schema(description = "总行数（分页时有效，-1 表示未统计）")
    private long total = -1;

    /** 当前页码（分页时有效）。 */
    @Builder.Default
    @Schema(description = "当前页码（分页时有效）")
    private int pageNum = 1;

    /** 每页行数（分页时有效）。 */
    @Builder.Default
    @Schema(description = "每页行数（分页时有效）")
    private int pageSize = 0;

    /** SQL 执行错误信息（非空时表示 SQL 执行失败）。 */
    @Schema(description = "SQL 执行错误信息（非空时表示 SQL 执行失败）")
    private String errorMessage;
}
