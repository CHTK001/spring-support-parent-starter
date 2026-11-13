package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 主机文件目录页。
 *
 * <p>{@code total} 是命中条数（有搜索词时是过滤后的数量），{@code scanned} 是本次实际
 * 扫描的条目数：目录太大时只扫描前若干条，{@code truncated} 置真由前端提示
 * 「只看了前 N 项」，而不是假装目录只有这么多。</p>
 *
 * @param path      当前目录 key
 * @param keyword   搜索词
 * @param items     当前页条目
 * @param total     命中总数
 * @param scanned   本次扫描条目数
 * @param truncated 是否因目录过大而未扫全
 * @author CH
 * @since 4.0.0.62
 */
public record HostFilePageVO(
        String path,
        String keyword,
        List<HostFileEntryVO> items,
        long total,
        int scanned,
        boolean truncated
) {

    /**
     * 规范构造器：条目做防御性拷贝。
     *
     * @param path      目录 key
     * @param keyword   搜索词
     * @param items     条目
     * @param total     命中总数
     * @param scanned   扫描条目数
     * @param truncated 是否截断
     */
    public HostFilePageVO {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
