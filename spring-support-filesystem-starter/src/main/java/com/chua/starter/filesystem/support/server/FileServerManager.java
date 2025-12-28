package com.chua.starter.filesystem.support.server;

import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.protocol.filter.FileStorageServletFilter;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.common.support.protocol.storage.FileStorageFactory;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.common.support.logger.ModuleLog.*;

/**
 * 文件服务器管理器
 * <p>
 * 管理多个 HTTP 文件服务器实例，支持多端口监听
 * </p>
 *
 * @author CH
 * @since 2024/12/28
 */
@Slf4j
public class FileServerManager implements AutoCloseable {

    /**
     * 服务器实例映射
     */
    @Getter
    private final Map<String, ProtocolServer> serverMap = new ConcurrentHashMap<>();

    /**
     * 配置属性
     */
    private final FileStorageProperties properties;

    /**
     * 存储模板
     */
    private final FileStorageTemplate storageTemplate;

    public FileServerManager(FileStorageProperties properties, FileStorageTemplate storageTemplate) {
        this.properties = properties;
        this.storageTemplate = storageTemplate;
    }

    /**
     * 启动所有配置的文件服务器
     */
    public void startAll() {
        List<FileStorageProperties.ServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("[FileServer] 未配置文件服务器，跳过启动");
            return;
        }

        log.info("[FileServer] ══════════════════════════════════════════════");
        log.info("[FileServer] 文件服务器配置");
        log.info("[FileServer] ├─ 服务器数量: {}", highlight(servers.size()));

        for (FileStorageProperties.ServerConfig serverConfig : servers) {
            if (!serverConfig.isEnable()) {
                log.info("[FileServer] ├─ 跳过禁用的服务器: {}", serverConfig.getName());
                continue;
            }

            try {
                startServer(serverConfig);
            } catch (Exception e) {
                log.error("[FileServer] 启动服务器失败: {} - {}", serverConfig.getName(), e.getMessage(), e);
            }
        }

        log.info("[FileServer] ══════════════════════════════════════════════");
    }

    /**
     * 启动单个文件服务器
     */
    private void startServer(FileStorageProperties.ServerConfig config) throws Exception {
        String serverName = config.getName();
        String host = config.getHost();
        int port = config.getPort();

        // 创建服务器设置
        ServerSetting serverSetting = ServerSetting.builder()
                .host(host)
                .port(port)
                .sslEnabled(config.isSsl())
                .readTimeoutMillis(config.getReadTimeoutMillis())
                .writeTimeoutMillis(config.getWriteTimeoutMillis())
                .build();

        // 创建协议服务器
        ProtocolServer server = ProtocolServer.create("http", serverSetting);

        // 创建文件存储设置
        FileStorageFactory.FileStorageSetting storageSetting = FileStorageFactory.FileStorageSetting.builder()
                .openPreview(properties.isOpenPreview())
                .openDownload(properties.isOpenDownload())
                .openRange(properties.isOpenRange())
                .openWatermark(properties.isOpenWatermark())
                .openWebjars(properties.isOpenWebjars())
                .openRemoteFile(properties.isOpenRemoteFile())
                .watermark(properties.getWatermark())
                .build();

        // 创建文件存储过滤器
        FileStorageServletFilter fileStorageFilter = new FileStorageServletFilter(storageSetting);

        // 注册存储到过滤器
        registerStoragesToFilter(fileStorageFilter, config.getStorageNames());

        // 添加过滤器
        server.addFilter(fileStorageFilter);

        // 启动服务器
        server.start();

        // 保存服务器实例
        serverMap.put(serverName, server);

        log.info("[FileServer] ├─ 启动服务器: {} -> {} [{}]",
                highlight(serverName),
                address(host, port),
                success());
    }

    /**
     * 注册存储到过滤器
     */
    private void registerStoragesToFilter(FileStorageServletFilter filter, List<String> storageNames) {
        Map<String, FileStorage> storageMap = storageTemplate.getStorageMap();

        if (storageNames == null || storageNames.isEmpty()) {
            // 注册所有存储
            for (Map.Entry<String, FileStorage> entry : storageMap.entrySet()) {
                filter.addFileStorage(entry.getKey(), entry.getValue());
                log.debug("[FileServer]   ├─ 注册存储: {}", entry.getKey());
            }
        } else {
            // 仅注册指定的存储
            for (String name : storageNames) {
                FileStorage storage = storageMap.get(name);
                if (storage != null) {
                    filter.addFileStorage(name, storage);
                    log.debug("[FileServer]   ├─ 注册存储: {}", name);
                } else {
                    log.warn("[FileServer]   ├─ 存储不存在: {}", name);
                }
            }
        }
    }

    /**
     * 停止所有服务器
     */
    public void stopAll() {
        for (Map.Entry<String, ProtocolServer> entry : serverMap.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("[FileServer] 停止服务器: {} [{}]", entry.getKey(), success());
            } catch (Exception e) {
                log.error("[FileServer] 停止服务器失败: {} [{}]", entry.getKey(), failed(), e);
            }
        }
        serverMap.clear();
    }

    /**
     * 获取服务器
     *
     * @param name 服务器名称
     * @return 服务器实例
     */
    public ProtocolServer getServer(String name) {
        return serverMap.get(name);
    }

    /**
     * 检查服务器是否运行中
     *
     * @param name 服务器名称
     * @return 是否运行中
     */
    public boolean isRunning(String name) {
        ProtocolServer server = serverMap.get(name);
        return server != null && server.isRunning();
    }

    @Override
    public void close() {
        stopAll();
    }
}
