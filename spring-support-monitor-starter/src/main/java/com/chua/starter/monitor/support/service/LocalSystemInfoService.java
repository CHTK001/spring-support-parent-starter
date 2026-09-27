package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessSummaryVO;
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
     * 获取本机基本信息（CPU、内存、操作系统、网络接口、包管理器），不探测公网 IP。
     *
     * <p>时间轮周期采集走本方法：公网 IP 基本不变且需外网往返，不应出现在周期采集路径。</p>
     *
     * @return 本机基本信息；OSHI 不可用时降级为 JVM 系统属性
     */
    default HostInfoVO getHostInfo() {
        return getHostInfo(false);
    }

    /**
     * 获取本机基本信息，可选是否探测公网 IP。
     *
     * @param includePublicIp true 时发起外网出口探测（仅用于点击「刷新」的按需画像接口）；
     *                        false 时公网 IP 留空，供时间轮高频周期采集使用
     * @return 本机基本信息；OSHI 不可用时降级为 JVM 系统属性
     */
    HostInfoVO getHostInfo(boolean includePublicIp);

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
     * 获取本机进程与线程总数。
     *
     * <p>不取命令行与可执行路径——这两项在 Windows 上按进程补全，实测全量约 18 秒；
     * 概览只需要计数，故走整表快照（实测约 0.3 秒）。</p>
     *
     * @return 计数结果，采集失败时为零值
     */
    ProcessSummaryVO getProcessSummary();

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

    /**
     * 获取本机磁盘读写速率（KB/s，瞬时）。
     *
     * @return 长度 2 的数组：[读取 KB/s, 写入 KB/s]；首次采样或失败为 0
     */
    double[] getDiskIoKbs();

    /**
     * 获取本机网卡收发吞吐（KB/s，瞬时）。
     *
     * @return 长度 2 的数组：[接收 KB/s, 发送 KB/s]；首次采样或失败为 0
     */
    double[] getNetworkThroughputKbs();

    /**
     * 获取本机运行的已知软件实例（按进程特征识别，支持多实例）。
     *
     * @return 软件实例列表；无已知软件时为空列表
     */
    List<com.chua.starter.server.support.software.SoftwareInstanceVO> getSoftware();
}
