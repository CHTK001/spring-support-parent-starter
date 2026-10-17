package com.chua.starter.monitor.support.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件浏览条目视图。
 *
 * <p>目录标志对外仍用 {@code isDir} 键，保持既有前端契约不变。</p>
 *
 * @param name      文件名称
 * @param directory 是否目录（JSON 键固定为 {@code isDir}）
 * @param size      字节大小
 * @param modified  修改时间展示文本
 * @param owner     属主，通道未上报时为空串
 * @param perms     权限串，通道未上报时为空串
 * @param group     属组，通道未上报时为空串
 * @author CH
 * @since 4.0.0.42
 */
public record FileEntryVO(
        String name,
        @JsonProperty("isDir") boolean directory,
        long size,
        String modified,
        String owner,
        String perms,
        String group) {
}
