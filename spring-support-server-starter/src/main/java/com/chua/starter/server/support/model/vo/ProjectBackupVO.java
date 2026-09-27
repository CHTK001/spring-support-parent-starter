package com.chua.starter.server.support.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目备份记录视图对象（对应项目目录 backups/ 下的一个备份）。
 *
 * @author CH
 */
@Data
@Schema(description = "项目备份记录")
public class ProjectBackupVO {

    /**
     * 备份名称（Linux 为 backup_<millis>.tar.gz；Windows 为 backup_<stamp> 目录名）
     */
    @Schema(description = "备份名称")
    private String name;

    /**
     * 备份时间（epoch 毫秒）
     */
    @Schema(description = "备份时间(epoch毫秒)")
    private Long time;

    /**
     * 备份大小（字节；Windows 目录镜像可能为 null）
     */
    @Schema(description = "备份大小(字节)")
    private Long size;

    /**
     * 备份类型：file=压缩包；dir=目录镜像
     */
    @Schema(description = "备份类型: file/dir")
    private String kind;
}
