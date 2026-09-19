package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;

import java.util.List;

/**
 * 本机系统信息采集服务。
 *
 * <p>封装 OSHI 与 Windows 服务枚举，是 {@code LocalOshiProvider} 这条 SPI 通道的采集内核；
 * 调用方面向本接口编程，不感知底层是 OSHI、PowerShell 还是回落实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface LocalSystemInfoService {

    /**
     * 获取本机基本信息（CPU、内存、操作系统、网络接口、包管理器）。
     *
     * @return 本机基本信息；OSHI 不可用时降级为 JVM 系统属性
     */
    HostInfoVO getHostInfo();

    /**
     * 获取本机 CPU 使用率（%，瞬时）。
     *
     * @return CPU 使用率 0-100；采集失败返回 0
     */
    double getCpuPercent();

    /**
     * 获取本机磁盘信息（文件系统挂载点）。
     *
     * @return 磁盘列表；采集失败返回空列表
     */
    List<DiskRowVO> getDisks();

    /**
     * 获取本机进程列表（按 CPU 占用倒序）。
     *
     * @param keyword 进程名或命令行关键字，为空则不做过滤
     * @param limit   最大返回条数
     * @return 进程列表；采集失败返回空列表
     */
    List<ProcessRowVO> getProcesses(String keyword, int limit);

    /**
     * 获取本机服务列表。
     *
     * @return 服务列表（名称、状态、启动类型、PID）；采集失败返回空列表
     */
    List<ServiceRowVO> getServices();

    /**
     * 获取本机网络接口。
     *
     * @return 网卡列表；采集失败返回空列表
     */
    List<NetworkInterfaceRowVO> getNetworkInterfaces();

    /**
     * 获取本机网络连接。
     *
     * @return 端口占用列表；采集失败返回空列表
     */
    List<NetProcessRowVO> getNetworkConnections();

    /**
     * 获取本机环境变量。
     *
     * @return 环境变量列表；无变量时返回空列表
     */
    List<EnvVarVO> getEnvVars();
}
