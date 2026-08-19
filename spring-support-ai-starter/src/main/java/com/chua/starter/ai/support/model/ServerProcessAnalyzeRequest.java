package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 进程分析请求
 *
 * @since 2026/7/30
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerProcessAnalyzeRequest {

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 服务器编码
     */
    private String serverCode;

    /**
     * 服务器类型
     */
    private String serverType;

    /**
     * 操作系统类型
     */
    private String osType;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 进程 PID
     */
    private Long pid;

    /**
     * 进程名称
     */
    private String name;

    /**
     * 进程所属用户
     */
    private String user;

    /**
     * 进程状态
     */
    private String state;

    /**
     * CPU 占用率
     */
    private Double cpuPercent;

    /**
     * 内存占用率
     */
    private Double memoryPercent;

    /**
     * 内存占用（字节）
     */
    private Long memoryBytes;

    /**
     * 线程数
     */
    private Integer threadCount;

    /**
     * 运行时长描述
     */
    private String elapsed;

    /**
     * 启动时间
     */
    private String startTime;

    /**
     * 启动命令行
     */
    private String commandLine;

    /**
     * Top 进程列表
     */
    private List<String> topProcesses;
}
