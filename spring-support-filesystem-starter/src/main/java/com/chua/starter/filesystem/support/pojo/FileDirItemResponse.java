package com.chua.starter.filesystem.support.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 服务器目录浏览条目。
 *
 * <p>目录与文件共用一种条目：{@code isDir} 区分两者，{@code size} 仅文件有值。
 * 前端选择器（ReFilePicker）直接用该结构渲染「名称 / 修改时间 / 类型 / 大小」，
 * 目录模式下文件只展示、不可选，避免用户停在空目录上无法确认选中的是不是目标目录。</p>
 *
 * @param name      目录名 / 文件名（父级条目为 ..）
 * @param path      绝对路径（已无父级时为空串）
 * @param isDir     是否目录
 * @param size      文件大小（字节）；目录为 null
 * @param updatedAt 修改时间（毫秒）；取不到为 null
 * @since 4.0.0.42
 * @author CH
 */
public record FileDirItemResponse(
        String name,
        String path,
        @JsonProperty("isDir") Boolean isDir,
        Long size,
        Long updatedAt) {
}
