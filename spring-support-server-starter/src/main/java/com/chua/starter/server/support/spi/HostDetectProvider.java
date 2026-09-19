package com.chua.starter.server.support.spi;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;

/**
 * 主机硬件探测提供者 SPI（按系统类型扩展）
 * <p>
 * 对目标主机执行硬件/系统信息采集（CPU/内存/磁盘/系统版本/架构/IP），
 * 按主机系统类型路由到不同实现（如 Linux 走 SSH、macOS 走 SSH-BSD 命令、
 * Windows 走 WinRM PowerShell）。新增系统类型只需实现本接口、标注 {@code @Spi}
 * 并登记到 {@code META-INF/extensions}，注册中心按 {@link #osType()} 自动路由，探测入口零改动。
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
     * 探测是否需要登录凭据。
     *
     * <p>SSH / WinRM 通道必须绑定登录身份；Agent / Local 通道由主机侧自报信息，
     * 返回 false 时调用方跳过凭据解析并以 null 传入 {@link #detect}。</p>
     *
     * @return 需要凭据返回 true
     */
    default boolean requiresCredential() {
        return true;
    }

    /**
     * 探测并回写主机硬件/系统信息
     *
     * @param host       目标主机（已存在，含地址/端口/系统类型）
     * @param credential 解密后的登录凭据（SSH 或 WinRM 共用）；{@link #requiresCredential()} 为 false 时为 null
     * @return 更新后的主机
     */
    ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential);

    /**
     * 测试通道连通性（不依赖 SSH 的主机类型用此入口）。
     *
     * @param host 目标主机
     * @return 连通结果说明
     */
    default ReturnResult<String> test(MsServerHost host) {
        return ReturnResult.error("该类型主机不支持测试连接: " + host.getServerHostOsType());
    }
}
