package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 流程节点。
 *
 * <p>坐标 x/y 仅服务于可视化布局，执行引擎不读取。</p>
 *
 * @param id     节点ID，流程内唯一
 * @param shape  节点类型，对应执行器 supportShape
 * @param name   节点显示名称
 * @param x      画布横坐标
 * @param y      画布纵坐标
 * @param params 节点参数
 * @param fields 抽取字段声明，仅抽取节点使用
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "流程节点")
public record CollectNodeDTO(
        @Schema(description = "节点ID")
        String id,
        @Schema(description = "节点类型")
        String shape,
        @Schema(description = "节点名称")
        String name,
        @Schema(description = "画布横坐标")
        Integer x,
        @Schema(description = "画布纵坐标")
        Integer y,
        @Schema(description = "节点参数")
        List<CollectParamDTO> params,
        @Schema(description = "抽取字段声明")
        List<CollectExtractFieldDTO> fields) {

    /**
     * 规范化集合组件：空值按空列表处理（与原字段初始值一致），并阻断外部别名写入。
     */
    public CollectNodeDTO {
        params = params == null ? List.of() : List.copyOf(params);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
