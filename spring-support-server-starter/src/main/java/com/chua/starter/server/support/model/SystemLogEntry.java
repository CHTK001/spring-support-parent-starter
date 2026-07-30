package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogEntry {
    private Long id;
    private String timestamp;
    private String level;
    private String source;
    private String message;
    private Long pid;
    private String processName;
    private String hostname;
    private String raw;
}
