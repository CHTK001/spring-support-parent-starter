package com.chua.starter.datasourcehub.support.model;

/**
 * etcd 键列表项（不含值，值由详情接口按需读取）。
 *
 * @param key            键名
 * @param createRevision 创建时的 revision
 * @param modRevision    最后修改的 revision
 * @param version        键的版本号，每次写入自增
 * @param lease         绑定的租约 ID，未绑定为 0
 * @author CH
 * @since 4.0.0.43
 */
public record EtcdKeyVO(
        String key,
        Long createRevision,
        Long modRevision,
        Long version,
        Long lease
) {
}
