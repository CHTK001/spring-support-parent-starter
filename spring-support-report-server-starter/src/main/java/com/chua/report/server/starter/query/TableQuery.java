package com.chua.report.server.starter.query;

import com.chua.starter.mybatis.entity.Query;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表查询
 * @author CH
 * @since 2024/9/27
 */
@Data
@Schema(description = "表查询")
public class TableQuery extends Query<TableQuery> {

    /**
     * 生成ID
     */
    @Schema(description = "生成ID")
    private Integer genId;

    /**
     * 表名
     */
    @Schema(description = "表名")
    private String[] tableNames;

    /**
     * 关键字
     */
    @Schema(description = "关键字")
    private String keyword;
}
