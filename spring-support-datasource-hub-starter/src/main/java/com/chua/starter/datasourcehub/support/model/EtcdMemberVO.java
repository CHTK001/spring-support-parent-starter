package com.chua.starter.datasourcehub.support.model;

/**
 * etcd 集群成员（含该成员的健康与状态）。
 *
 * <p>集群成员的 clientURLs 是列表，页面展示取第一个；状态字段来自对**每个成员**各自调用
 * {@code /v3/maintenance/status} 的结果，某个成员不可达时 {@code healthy} 为假、其余字段留空，
 * 而不是把整个列表打成失败——集群里挂一个成员是常见故障，页面要能看出来是哪一个。</p>
 *
 * @param id         成员 ID（十六进制）
 * @param name       成员名，未命名时为空
 * @param peerUrls   peer 地址列表（逗号分隔）
 * @param clientUrls client 地址列表（逗号分隔）
 * @param leader     是否为当前 leader
 * @param healthy    该成员是否可达且健康
 * @param version    该成员的 etcd 版本，不可达时为空
 * @param dbSize     该成员的数据文件大小（字节），不可达时为 null
 * @param error      不可达时的可读原因，健康时为空
 * @author CH
 * @since 4.0.0.43
 */
public record EtcdMemberVO(
        String id,
        String name,
        String peerUrls,
        String clientUrls,
        Boolean leader,
        Boolean healthy,
        String version,
        Long dbSize,
        String error
) {
}
