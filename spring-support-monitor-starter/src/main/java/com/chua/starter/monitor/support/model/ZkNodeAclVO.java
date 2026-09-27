package com.chua.starter.monitor.support.model;

/**
 * ZooKeeper 节点 ACL 条目。
 *
 * @param scheme    权限方案，如 world/auth/digest/ip
 * @param id        方案下的身份标识，如 anyone、user:pass
 * @param perms     权限位掩码（1=READ,2=WRITE,4=CREATE,8=DELETE,16=ADMIN）
 * @param permsText 可读权限组合，如 READ|WRITE|CREATE
 * @author CH
 * @since 4.0.0.43
 */
public record ZkNodeAclVO(
        String scheme,
        String id,
        Integer perms,
        String permsText
) {
}
