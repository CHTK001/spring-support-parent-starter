package com.chua.report.client.starter.report.event;


import lombok.Data;

import java.util.List;

/**
 * 进程事件类，用于描述进程的相关信息和状态
 *
 * @author CH
 * @since 2024/9/21
 */
@Data
public class ProcessEvent {

    /**
     * 进程命令
     */
    private String command;
    /**
     * 进程ID
     */
    private long processId;
    /**
     * 进程名称
     */
    private String name;
    /**
     * 进程状态
     */
    private String status;
    /**
     * 进程开始时间
     */
    private long startTime;
    /**
     * 进程所属用户
     */
    private String user;

    /**
     * 进程内存使用量
     */
    private long value;
    /**
     * 进程运行时间
     */
    private long upTime;
    /**
     * 进驻集大小，进程在内存中实际使用的物理内存大小
     */
    private long residentSetSize;
    /**
     * 虚拟内存大小
     */
    private long virtualSize;
    /**
     * 进程事件ID
     */
    private String id;

    /**
     * 父进程ID
     */
    private int parentId;

    /**
     * 子进程列表
     */
    private List<ProcessEvent> children;
}
