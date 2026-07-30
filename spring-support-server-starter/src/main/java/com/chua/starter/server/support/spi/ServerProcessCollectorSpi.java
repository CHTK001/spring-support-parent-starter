package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;

/**
 * 进程采集 SPI。
 *
 * <p>按协议封装进程列表命令、终止命令与解析类型，避免服务层堆积协议分支。
 *
 * @author CH
 */
public interface ServerProcessCollectorSpi {

    /**
     * 构建进程列表采集命令。
     *
     * @param host 服务器主机
     * @param queryWindow 查询窗口大小
     * @return 可执行命令
     */
    String buildListCommand(ServerHost host, int queryWindow);

    /**
     * 构建终止进程命令。
     *
     * @param host 服务器主机
     * @param pid 目标进程 ID
     * @param force 是否强制终止
     * @return 可执行命令
     */
    String buildTerminateCommand(ServerHost host, Long pid, boolean force);

    /**
     * 当前协议采集结果是否按 Windows 格式解析。
     *
     * @param host 服务器主机
     * @return true 表示 Windows 解析
     */
    boolean windowsParser(ServerHost host);

    /**
     * 是否允许本机兜底 ProcessHandle。
     *
     * @param host 服务器主机
     * @return true 表示允许兜底
     */
    default boolean supportsLocalFallback(ServerHost host) {
        return false;
    }
}

