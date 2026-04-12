package com.chua.starter.server.support.spi;

import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.starter.server.support.entity.ServerHost;

/**
 * 服务器接入协议 SPI。
 *
 * <p>统一封装 LOCAL/SSH/WINRM 在默认端口、基础目录、文件通道上的差异，
 * 避免各业务服务自行判断协议类型。
 */
public interface ServerHostProtocolSpi {

    /**
     * 是否为本机协议。
     */
    default boolean isLocal() {
        return false;
    }

    /**
     * 当前协议默认端口。
     */
    int getDefaultPort();

    /**
     * 当前协议默认主机地址。
     */
    String getDefaultHost(ServerHost host);

    /**
     * 当前协议是否按 Windows 语义处理。
     */
    boolean isWindows(ServerHost host);

    /**
     * 当前协议默认基础目录。
     */
    default String getDefaultBaseDirectory(ServerHost host) {
        return isWindows(host) ? "C:/" : "/";
    }

    /**
     * 为文件管理创建协议对应的文件客户端。
     *
     * <p>本机协议无需远程文件客户端，可返回 {@code null}。
     */
    default FileClient createFileClient(ServerHost host) {
        return null;
    }
}
