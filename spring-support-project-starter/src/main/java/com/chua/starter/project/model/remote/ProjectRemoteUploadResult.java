package com.chua.starter.project.model.remote;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectRemoteUploadResult {
    private boolean success;
    private String message;
    private String uploadedPath;
    private String extractedPath;
    private String backupPath;
    private Integer filesCount;
}
