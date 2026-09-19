package com.chua.starter.monitor.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 系统信息提供者 SPI。
 *
 * <p>按主机接入方式提供统一的系统信息采集能力，共 5 种实现：
 * Local(OSHI) / Linux-SSH / Mac-SSH / Windows-WinRM / Agent-HTTP。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SystemInfoProvider {

    /**
     * 是否支持采集该主机。
     *
     * @param host 目标主机
     * @return 支持返回 true
     */
    boolean supports(MsServerHost host);

    /**
     * 主机基础信息（CPU / 内存 / 操作系统等）。
     *
     * @param host 目标主机
     * @return 基础信息键值对
     */
    Map<String, Object> hostInfo(MsServerHost host);

    /**
     * 分页查询进程列表。
     *
     * @param host     目标主机
     * @param page     页码
     * @param pageSize 每页条数
     * @return 进程列表
     */
    List<Map<String, Object>> processes(MsServerHost host, int page, int pageSize);

    /**
     * 分页查询服务列表。
     *
     * @param host     目标主机
     * @param page     页码
     * @param pageSize 每页条数
     * @return 服务列表
     */
    List<Map<String, Object>> services(MsServerHost host, int page, int pageSize);

    /**
     * 磁盘列表。
     *
     * @param host 目标主机
     * @return 磁盘信息列表
     */
    List<Map<String, Object>> disks(MsServerHost host);

    /**
     * 网络接口列表。
     *
     * @param host 目标主机
     * @return 网卡信息列表
     */
    List<Map<String, Object>> networkInterfaces(MsServerHost host);

    /**
     * 网络端口占用列表。
     *
     * @param host 目标主机
     * @return 端口占用列表
     */
    List<Map<String, Object>> netProcesses(MsServerHost host);

    /**
     * 目录文件列表。
     *
     * @param host 目标主机
     * @param path 目录路径
     * @return 文件条目列表
     */
    List<Map<String, Object>> files(MsServerHost host, String path);

    /**
     * 上传文件到目标主机。
     *
     * @param host       目标主机
     * @param remotePath 远端路径
     * @param data       数据流
     * @param size       数据长度
     * @return 成功返回 true
     */
    boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size);

    /**
     * 下载目标主机文件。
     *
     * @param host       目标主机
     * @param remotePath 远端路径
     * @return 文件输入流，失败返回 null
     */
    InputStream downloadFile(MsServerHost host, String remotePath);

    /**
     * 在目标主机执行命令。
     *
     * @param host 目标主机
     * @param cmd  命令
     * @return 标准输出内容
     */
    String exec(MsServerHost host, String cmd);

    /**
     * 环境变量列表。
     *
     * @param host 目标主机
     * @return 环境变量列表
     */
    List<Map<String, Object>> env(MsServerHost host);
}
