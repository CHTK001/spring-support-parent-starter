package com.chua.starter.project.model.remote;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectRemoteFileOperationResult {
    private boolean success;
    private String message;
    private String path;
    private String targetPath;
}
