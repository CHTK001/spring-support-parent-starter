package com.chua.report.server.starter.query;

import com.chua.starter.mybatis.entity.Query;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表查询
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "子节点查询")
public class NodeChildrenQuery extends Query<NodeChildrenQuery> {

    /**
     * id
     */
    @Schema(description = "id")
    private Integer genId;


    /**
     * 节点类型
     */
    @Schema(description = "节点类型")
    private String nodeType;

    /**
     * 节点id
     */
    @Schema(description = "节点id")
    private String nodeId;
    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    private String nodeName;

    /**
     * 关键字
     */
    @Schema(description = "关键字")
    private String keyword;

}
