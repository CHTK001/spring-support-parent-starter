package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 进程分析请求
 *
 * @author CH
 * @since 2026/7/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerProcessAnalyzeRequest {

    private String serverName;
    private String serverCode;
    private String serverType;
    private String osType;
    private String host;
    private Long pid;
    private String name;
    private String user;
    private String state;
    private Double cpuPercent;
    private Double memoryPercent;
    private Long memoryBytes;
    private Integer threadCount;
    private String elapsed;
    private String startTime;
    private String commandLine;
    private List<String> topProcesses;
}
