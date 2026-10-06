package com.chua.starter.datasourcehub.support.model;

/**
 * ZooKeeper 节点详情（含完整 Stat）。
 *
 * <p>保留原有展示字段，补全 czxid/mzxid/pzxid/cversion/aversion/ephemeralOwner 等
 * Stat 原始字段，便于页面完整呈现节点元信息。</p>
 *
 * @param path          绝对路径
 * @param ephemeral     是否临时节点
 * @param createdAt     创建时间（可读文本，取自 ctime）
 * @param updatedAt     最后修改时间（可读文本，取自 mtime）
 * @param version       数据版本号，每次写数据自增
 * @param numChildren   子节点数量
 * @param dataLength    数据字节长度
 * @param data          节点数据（UTF-8 文本，二进制时以占位说明呈现）
 * @param czxid         创建该节点的事务 zxid
 * @param mzxid         最后修改该节点的事务 zxid
 * @param pzxid         最后修改其子节点列表的事务 zxid
 * @param cversion      子节点版本号
 * @param aversion      ACL 版本号
 * @param ephemeralOwner 临时节点会话 ID，持久节点为 0
 * @author CH
 * @since 4.0.0.42
 */
public record ZkNodeDetailVO(
        String path,
        Boolean ephemeral,
        String createdAt,
        String updatedAt,
        Integer version,
        Integer numChildren,
        Integer dataLength,
        String data,
        Long czxid,
        Long mzxid,
        Long pzxid,
        Integer cversion,
        Integer aversion,
        Long ephemeralOwner
) {
}
