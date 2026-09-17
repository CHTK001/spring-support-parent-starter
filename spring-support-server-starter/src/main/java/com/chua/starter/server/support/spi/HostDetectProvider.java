package com.chua.starter.server.support.spi;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;

/**
* 主机硬件探测提供者 SPI（按系统类型扩展）
* <p>
* 对目标主机执行硬件/系统信息采集（CPU/内存/磁盘/系统版本/架构/IP），
* 按主机系统类型路由到不同实现（如 Linux 走 SSH、macOS 走 SSH-BSD 命令、
* Windows 走 WinRM PowerShell）。新增系统类型只需实现本接口并注册为
* Spring Bean，注册中心按 {@link #osType()} 自动路由，探测入口零改动。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
public interface HostDetectProvider {

    /**
    * 支持的操作系统类型（与 MsServerHost.serverHostOsType 对齐，如 LINUX/MACOS/WINDOWS）
    *
    * @return 系统类型标识（全大写）
    */
    String osType();

    /**
    * 探测并回写主机硬件/系统信息
    *
    * @param host       目标主机（已存在，含地址/端口/系统类型）
    * @param credential 解密后的登录凭据（SSH 或 WinRM 共用）
    * @return 更新后的主机
    */
    ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential);
}
