package com.chua.starter.project.model.remote;

import lombok.Data;

@Data
public class ProjectRemoteFileRenameRequest {
    private String path;
    private String targetPath;
}
