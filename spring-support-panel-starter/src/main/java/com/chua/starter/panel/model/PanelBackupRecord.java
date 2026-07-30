package com.chua.starter.panel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份记录。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "备份记录")
public class PanelBackupRecord {

    @Schema(description = "备份 ID")
    private String backupId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private long fileSize;

    @Schema(description = "备份时间")
    private LocalDateTime backupTime;

    @Schema(description = "备份类型（full/ibd）")
    private String backupType;

    @Schema(description = "描述")
    private String description;
}
