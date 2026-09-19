package com.chua.starter.monitor.support.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 文件浏览条目视图。
 *
 * <p>目录标志对外仍用 {@code isDir} 键，保持既有前端契约不变。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class FileEntryVO {

    /**
     * 文件名称
     */
    private String name;

    /**
     * 是否目录
     */
    @JsonProperty("isDir")
    private boolean directory;

    /**
     * 字节大小
     */
    private long size;

    /**
     * 修改时间展示文本
     */
    private String modified;

    /**
     * 属主，通道未上报时为空串
     */
    private String owner;

    /**
     * 权限串，通道未上报时为空串
     */
    private String perms;

    /**
     * 属组，通道未上报时为空串
     */
    private String group;
}
