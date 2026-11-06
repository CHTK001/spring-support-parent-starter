package com.chua.starter.collect.dto;

import com.chua.starter.collect.entity.CollectField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 结果字段整体保存入参。
 *
 * <p>字段定义决定结果表格列、去重键与导出列，保存时按流程节点整体覆盖。</p>
 *
 * @param collectFlowId 所属流程ID
 * @param collectNodeId 产出字段的节点ID，空表示流程级
 * @param fields        字段定义，顺序即列顺序
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "采集结果字段保存入参")
public record CollectFieldSaveDTO(
        @Schema(description = "所属流程ID")
        Integer collectFlowId,
        @Schema(description = "产出字段的节点ID")
        String collectNodeId,
        @Schema(description = "字段定义列表")
        List<CollectField> fields) {

    /**
     * 规范化集合组件，阻断外部别名写入；保留 null 语义。
     */
    public CollectFieldSaveDTO {
        fields = fields == null ? null : List.copyOf(fields);
    }
}
