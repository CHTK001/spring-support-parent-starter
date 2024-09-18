package com.chua.report.client.starter.report.event;

import lombok.Data;

/**
 * 系统cpu信息类
 * 该类用于封装系统CPU的相关信息，包括CPU数量、各种CPU时间占比以及CPU型号等
 * @author CH
 * @since 2024/9/18
 */
@Data
public class CpuEvent {

    /**
     * CPU核心数量
     */
    private Integer cpuNum;
    /**
     * 总的CPU时间占比
     */
    private double toTal;
    /**
     * 系统占用的CPU时间占比
     */
    private double sys;
    /**
     * 用户进程占用的CPU时间占比
     */
    private double user;
    /**
     * 等待时间占比，表示在I/O或其他资源等待的时间
     */
    private double wait;
    /**
     * 空闲时间占比，表示CPU处于空闲状态的时间
     */
    private double free;
    /**
     * CPU型号
     */
    private String cpuModel;
}
