package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 进程关系图谱数据。
 *
 * <p>包含节点和边，用于前端 G6 力导向图渲染。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerProcessRelationGraph {

    private List<Node> nodes;
    private List<Edge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private Long pid;
        private String name;
        private String command;
        private Double cpuPercent;
        private Double memoryPercent;
        private String status;
        private Boolean isTarget;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private Long source;
        private Long target;
        private String relation;
        private String label;
    }
}
