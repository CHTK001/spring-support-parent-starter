package com.chua.report.server.starter.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 节点数据
 * @author CH
 * @since 2024/9/27
 */
@Data
@Schema(description = "节点数据")
public class NodeData {

    /**
     * 节点pid
     */
    @Schema(description = "节点pid")
    private String nodePid;
    /**
     * 节点类型
     */
    @Schema(description = "节点类型")
    private String nodeType;

    /**
     * 节点是否叶子节点
     */
    @Schema(description = "节点是否叶子节点")
    private boolean nodeLeaf;

    /**
     * 节点注释
     */
    @Schema(description = "节点注释")
    private String nodeComment;
    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    private String nodeName;

    /**
     * 数据类型
     */
    @Schema(description= "数据类型")
    private String dataType;
    /**
     * 节点ID
     */
    @Schema(description = "节点ID")
    private String nodeId;


    /**
     * 子节点
     */
    @Schema(description = "子节点")
    private List<NodeData> children;
}
