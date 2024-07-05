package com.chua.starter.monitor.server.pojo;

import lombok.Data;
/**
 * W类用于封装w命令的输出信息。
 * w命令用于显示当前系统的用户、从哪里登录、登录时间、空闲时间以及系统CPU和进程CPU的使用情况。
 * w命令
 * @author CH
 * @since 2024/7/4
 */
@Data
public class W {

    /**
     * 当前登录用户的用户名。
     */
    private String user;

    /**
     * 用户从哪里登录到系统。
     */
    private String from;

    /**
     * 用户的登录时间。
     */
    private String loginTime;

    /**
     * 用户的空闲时间，即用户没有进行任何输入操作的时间长度。
     */
    private String idle;

    /**
     * 自系统启动以来，所有进程使用的CPU时间，包括用户时间和系统时间。
     */
    private String jcpu;

    /**
     * 当前进程使用的CPU时间，包括用户时间和系统时间。
     */
    private String pcpu;

    /**
     * 当前用户正在运行的命令或程序。
     */
    private String what;
}

