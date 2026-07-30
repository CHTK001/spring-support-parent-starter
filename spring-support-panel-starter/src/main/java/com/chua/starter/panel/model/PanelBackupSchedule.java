package com.chua.starter.panel.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份调度配置。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "备份调度配置")
public class PanelBackupSchedule {

    @Schema(description = "连接标识")
    private String connectionId;

    @Schema(description = "是否开启定时备份")
    private boolean enabled;

    @Schema(description = "cron 表达式")
    private String cronExpression;

    @Schema(description = "备份文件保存路径")
    private String backupPath;

    @Schema(description = "保留备份份数")
    private Integer keepCount;

    @Schema(description = "下次执行时间")
    private LocalDateTime nextRunTime;

    @Schema(description = "上次执行时间")
    private LocalDateTime lastRunTime;

    @Schema(description = "上次执行结果")
    private String lastResult;
}
