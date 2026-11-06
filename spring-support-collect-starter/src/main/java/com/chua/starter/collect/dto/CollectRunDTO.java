package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 流程运行入参。
 *
 * @param collectFlowId 流程ID
 * @param variables     本次运行的变量覆盖，同名覆盖流程全局变量
 * @param persist       是否把采集结果写入结果表，调试草稿时传 false
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "流程运行入参")
public record CollectRunDTO(
        @Schema(description = "流程ID")
        Integer collectFlowId,
        @Schema(description = "变量覆盖")
        List<CollectParamDTO> variables,
        @Schema(description = "结果是否落库")
        Boolean persist) {

    /**
     * 规范化集合组件，阻断外部别名写入；保留 null 语义。
     */
    public CollectRunDTO {
        variables = variables == null ? null : List.copyOf(variables);
    }
}
