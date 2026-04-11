package com.chua.starter.server.support.model;

import lombok.Data;

@Data
public class ServerFileWriteRequest {
    private String path;
    private String content;
}
