package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerProcessView {

    private Integer serverId;
    private String serverCode;
    private Long pid;
    private Long parentPid;
    private String name;
    private String command;
    private String commandLine;
    private String user;
    private String state;
    private Double cpuPercent;
    private Double memoryPercent;
    private Long memoryBytes;
    private Integer threadCount;
    private String elapsed;
    private String startTime;
    private Boolean alive;
    private String executionProvider;
    private String spiChannel;
}
