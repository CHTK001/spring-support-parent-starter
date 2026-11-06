package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 流程图 DSL。
 *
 * <p>序列化后存入 collect_flow.collect_flow_dsl，是可视化设计器与执行引擎之间的唯一契约。</p>
 *
 * @param name        流程名称，与流程表冗余一致，以 DSL 为准展示在设计器标题
 * @param threadCount 流程内并行线程数，0 或空取平台默认
 * @param timeoutMs   流程级单次运行最长毫秒；0 或空取平台全局 task-timeout-ms（默认 30 分钟）。
 *                   分片型大采集（如 GitHub 全量分片）可按需调大，避免在收尾阶段被超时强制结束
 * @param headers     全局请求头，所有抓取节点共享
 * @param cookies     全局 Cookie，所有抓取节点共享
 * @param variables   全局变量，执行前注入变量上下文
 * @param nodes       节点集合
 * @param edges       连线集合
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "流程图DSL")
public record CollectDslDTO(
        @Schema(description = "流程名称")
        String name,
        @Schema(description = "并行线程数")
        Integer threadCount,
        @Schema(description = "流程级运行超时毫秒，0/空取全局默认")
        Long timeoutMs,
        @Schema(description = "全局请求头")
        List<CollectParamDTO> headers,
        @Schema(description = "全局Cookie")
        List<CollectParamDTO> cookies,
        @Schema(description = "全局变量")
        List<CollectParamDTO> variables,
        @Schema(description = "节点集合")
        List<CollectNodeDTO> nodes,
        @Schema(description = "连线集合")
        List<CollectEdgeDTO> edges) {

    /**
     * 规范化集合组件：空值按空列表处理（与原字段初始值一致），并阻断外部别名写入。
     */
    public CollectDslDTO {
        headers = headers == null ? List.of() : List.copyOf(headers);
        cookies = cookies == null ? List.of() : List.copyOf(cookies);
        variables = variables == null ? List.of() : List.copyOf(variables);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
