package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerFileContent {
    private String path;
    private String content;
    private long size;
    private boolean truncated;
    private String language;
}
