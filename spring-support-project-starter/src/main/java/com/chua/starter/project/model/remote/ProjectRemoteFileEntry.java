package com.chua.starter.project.model.remote;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectRemoteFileEntry {
    private String name;
    private String path;
    private boolean directory;
    private boolean file;
    private Boolean hidden;
    private Long size;
    private Long lastModified;
    private String extension;
}
