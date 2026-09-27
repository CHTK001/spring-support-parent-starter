package com.chua.starter.filesystem.support.pojo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务器目录浏览条目。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Getter
@Setter
@Builder
public class FileDirItemResponse {

    /**
     * 目录名（父级条目为 ..）
     */
    private String name;

    /**
     * 目录绝对路径（已无父级时为空串）
     */
    private String path;
}
