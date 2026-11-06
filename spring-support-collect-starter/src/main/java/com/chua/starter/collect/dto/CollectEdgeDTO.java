package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 流程连线。
 *
 * <p>条件与异常流向挂在边上而非节点上，与 spider-flow 的
 * condition/exception-flow/transmit-variable 三件套语义一致。</p>
 *
 * @param id               连线ID
 * @param from             源节点ID
 * @param to               目标节点ID
 * @param condition        流转条件表达式，空表示无条件流转
 * @param exceptionFlow    异常流向：0 总是流转、1 仅上游异常时流转、2 仅上游正常时流转；
 *                         取值语义以 {@code com.chua.starter.collect.engine.model.CollectEdge} 的
 *                         {@code FLOW_ALWAYS / FLOW_EXCEPTION / FLOW_NORMAL} 常量为准，留空按 0 处理
 * @param transmitVariable 是否把上游变量传递给下游
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "流程连线")
public record CollectEdgeDTO(
        @Schema(description = "连线ID")
        String id,
        @Schema(description = "源节点ID")
        String from,
        @Schema(description = "目标节点ID")
        String to,
        @Schema(description = "流转条件表达式")
        String condition,
        @Schema(description = "异常流向 0总是流转 1仅异常时流转 2仅正常时流转")
        String exceptionFlow,
        @Schema(description = "是否传递上游变量")
        Boolean transmitVariable) {
}
