package com.chua.starter.project.model.remote;

import lombok.Data;

@Data
public class ProjectRemoteBackup {
    private String backupId;
    private String backupPath;
    private String backupTime;
    private Long size;
    private String note;
}
