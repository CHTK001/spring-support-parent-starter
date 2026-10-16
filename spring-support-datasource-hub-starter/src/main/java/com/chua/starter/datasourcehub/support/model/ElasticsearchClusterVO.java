package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 集群信息（集群配置面板用）
 *
 * @param clusterName       集群名
 * @param status            集群健康度
 * @param numberOfNodes     节点总数
 * @param numberOfDataNodes 数据节点数
 * @param activeShards      活跃分片数
 * @param unassignedShards  未分配分片数
 * @param initializingShards 初始化中的分片数
 * @param relocatingShards  迁移中的分片数
 * @param version           节点版本（取第一个节点）
 * @param nodes             节点列表
 * @param settings          集群设置（扁平化；含 persistent 与 defaults 的合并结果）
 * @author CH
 * @since 4.0.0.42
 */
public record ElasticsearchClusterVO(
        String clusterName,
        String status,
        Integer numberOfNodes,
        Integer numberOfDataNodes,
        Integer activeShards,
        Integer unassignedShards,
        Integer initializingShards,
        Integer relocatingShards,
        String version,
        List<Node> nodes,
        Map<String, Object> settings
) {

    /**
     * 集群节点
     *
     * @param name        节点名
     * @param ip          节点地址
     * @param roles       节点角色（如 dimr）
     * @param master      是否 master（* 表示是）
     * @param heapPercent 堆使用率
     * @param cpu         CPU 使用率
     * @param load        1 分钟负载
     * @param version     节点版本
     * @author CH
     * @since 4.0.0.42
     */
    public record Node(
            String name,
            String ip,
            String roles,
            String master,
            String heapPercent,
            String cpu,
            String load,
            String version
    ) {
    }
}
