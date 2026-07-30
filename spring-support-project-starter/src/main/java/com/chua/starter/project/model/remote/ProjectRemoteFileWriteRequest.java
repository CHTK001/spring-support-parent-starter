package com.chua.starter.project.model.remote;

import lombok.Data;

@Data
public class ProjectRemoteFileWriteRequest {
    private String path;
    private String content;
}
