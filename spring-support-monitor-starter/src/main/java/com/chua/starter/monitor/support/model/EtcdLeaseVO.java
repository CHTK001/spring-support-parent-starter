package com.chua.starter.monitor.support.model;

/**
 * etcd 租约（lease）。
 *
 * <p>租约是 etcd 的 TTL 机制：绑定租约的键会在租约到期时被自动删除，
 * 对应 ZooKeeper 的临时节点。{@code ttl} 是剩余秒数，{@code grantedTtl} 是授予时的原始 TTL。</p>
 *
 * @param id         租约 ID
 * @param ttl        剩余 TTL 秒数
 * @param grantedTtl 授予时的 TTL 秒数
 * @param keyCount   绑定到该租约的键数量
 * @author CH
 * @since 4.0.0.43
 */
public record EtcdLeaseVO(
        Long id,
        Long ttl,
        Long grantedTtl,
        Integer keyCount
) {
}
