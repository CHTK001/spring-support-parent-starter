package com.chua.starter.datasourcehub.support.model;

/**
 * ZooKeeper 连通性测试结果
 *
 * @param connected 是否连通
 * @param version   服务端版本，取自 {@code /zookeeper} 节点，读不到时为空串
 * @param sessionId 本次管理会话 ID（十六进制），临时节点的存活以此为界
 * @param message   结果说明或失败原因
 * @author CH
 * @since 4.0.0.42
 */
public record ZookeeperCheckVO(
        Boolean connected,
        String version,
        String sessionId,
        String message
) {
}
