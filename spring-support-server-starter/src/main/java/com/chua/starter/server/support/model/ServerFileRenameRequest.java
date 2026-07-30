package com.chua.starter.server.support.model;

import lombok.Data;

@Data
public class ServerFileRenameRequest {
    private String path;
    private String targetPath;
}
