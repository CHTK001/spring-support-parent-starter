package com.chua.starter.datasourcehub.support.model;

/**
 * etcd 服务端状态（{@code /v3/maintenance/status}）。
 *
 * <p>etcd 的 JSON 网关把所有数值都以**字符串**返回（如 {@code "dbSize":"24576"}），
 * 这里已统一解析成数值类型，页面直接按数字渲染。</p>
 *
 * @param version      etcd 版本
 * @param leader       leader 成员 ID（十六进制）
 * @param dbSize       数据文件大小（字节）
 * @param dbSizeInUse  实际使用大小（字节）
 * @param raftIndex    raft 已提交日志索引
 * @param raftTerm     raft 任期
 * @param revision     当前全局 revision（取自响应头）
 * @author CH
 * @since 4.0.0.43
 */
public record EtcdStatusVO(
        String version,
        String leader,
        Long dbSize,
        Long dbSizeInUse,
        Long raftIndex,
        Long raftTerm,
        Long revision
) {
}
