package com.chua.starter.project.model.remote;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectRemoteOperationResult {
    private boolean success;
    private String message;
    private String output;
    private Long pid;
    private String runtimeStatus;
    private String backupPath;
}
