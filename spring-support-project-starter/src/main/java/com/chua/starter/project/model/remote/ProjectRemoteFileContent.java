package com.chua.starter.project.model.remote;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectRemoteFileContent {
    private String path;
    private String content;
    private Integer size;
    private boolean truncated;
    private String language;
}
