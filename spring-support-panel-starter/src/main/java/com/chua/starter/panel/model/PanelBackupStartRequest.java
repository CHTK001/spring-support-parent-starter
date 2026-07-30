package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 备份启动请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanelBackupStartRequest {
    private String cronExpression;
    private String backupPath;
    private Integer keepCount;
}
