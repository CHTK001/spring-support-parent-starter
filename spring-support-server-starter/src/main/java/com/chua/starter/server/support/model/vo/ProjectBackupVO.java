package com.chua.starter.server.support.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 项目备份记录视图对象（对应项目目录 backups/ 下的一个备份）。
 *
 * @param name 备份名称（Linux 为 backup_&lt;millis&gt;.tar.gz；Windows 为 backup_&lt;stamp&gt; 目录名）
 * @param time 备份时间（epoch 毫秒）
 * @param size 备份大小（字节；Windows 目录镜像可能为 null）
 * @param kind 备份类型：file=压缩包；dir=目录镜像
 * @author CH
 */
@Schema(description = "项目备份记录")
public record ProjectBackupVO(
        @Schema(description = "备份名称")
        String name,
        @Schema(description = "备份时间(epoch毫秒)")
        Long time,
        @Schema(description = "备份大小(字节)")
        Long size,
        @Schema(description = "备份类型: file/dir")
        String kind) {
}
