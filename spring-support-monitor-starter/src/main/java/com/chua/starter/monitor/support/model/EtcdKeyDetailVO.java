package com.chua.starter.monitor.support.model;

/**
 * etcd 键详情（含值）。
 *
 * <p>etcd 的值是任意字节。可解析为 UTF-8 文本时原样给出；否则给出占位说明并把
 * {@code binary} 置真，避免把二进制内容塞进页面造成乱码。</p>
 *
 * @param key            键名
 * @param value          值（UTF-8 文本；二进制时为占位说明）
 * @param binary         值是否为不可解析的二进制
 * @param valueLength    值的字节长度
 * @param createRevision 创建时的 revision
 * @param modRevision    最后修改的 revision
 * @param version        键的版本号
 * @param lease          绑定的租约 ID，未绑定为 0
 * @param ttlSeconds     该租约剩余 TTL 秒数，未绑定或查询失败为 null
 * @author CH
 * @since 4.0.0.43
 */
public record EtcdKeyDetailVO(
        String key,
        String value,
        Boolean binary,
        Integer valueLength,
        Long createRevision,
        Long modRevision,
        Long version,
        Long lease,
        Long ttlSeconds
) {
}
