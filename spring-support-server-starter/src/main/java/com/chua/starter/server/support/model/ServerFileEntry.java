package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerFileEntry {
    private String name;
    private String path;
    private boolean directory;
    private boolean file;
    private boolean hidden;
    private long size;
    private long lastModified;
    private String extension;
}
