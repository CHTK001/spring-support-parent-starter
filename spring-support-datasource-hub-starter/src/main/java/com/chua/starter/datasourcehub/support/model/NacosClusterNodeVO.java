package com.chua.starter.datasourcehub.support.model;

/**
 * Nacos 集群节点。
 *
 * <p>Nacos 不同大版本在该接口里回传的字段差异较大：2.x 不再给出权重，角色只在部分版本的
 * {@code extendInfo} 里出现，取不到的字段一律留空而不是推断。</p>
 *
 * @param ip     节点地址
 * @param port   节点端口
 * @param status 节点状态（UP/DOWN）
 * @param role   角色（LEADER/FOLLOWER），服务端未给出时为 null
 * @param weight 节点权重，服务端未提供时为 null
 * @param leader 是否为 leader 节点
 * @author CH
 * @since 4.0.0.42
 */
public record NacosClusterNodeVO(
        String ip,
        Integer port,
        String status,
        String role,
        Integer weight,
        Boolean leader
) {
}
