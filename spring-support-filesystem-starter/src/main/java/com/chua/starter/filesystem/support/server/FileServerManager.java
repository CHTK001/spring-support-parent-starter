package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.starter.common.support.logger.ModuleLog;
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
    private final Map<String, Server> serverMap = new ConcurrentHashMap<>();

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
            log.info("未配置文件服务器，跳过启动");
            return;
        }

        log.info("══════════════════════════════════════════════");
        log.info("文件服务器配置");
        log.info("├─ 服务器数量: {}", highlight(servers.size()));

        for (FileStorageProperties.ServerConfig serverConfig : servers) {
            if (!serverConfig.isEnable()) {
                log.info("├─ 跳过禁用的服务器: {}", serverConfig.getName());
                continue;
            }

            try {
                startServer(serverConfig);
            } catch (Exception e) {
                log.error("启动服务器失败: {} - {}", serverConfig.getName(), e.getMessage(), e);
            }
        }

        log.info("══════════════════════════════════════════════");
    }

    /**
     * 启动单个文件服务器
     */
    private void startServer(FileStorageProperties.ServerConfig config) throws Exception {
        String serverName = config.getName();
        String host = config.getHost();
        int port = config.getPort();

        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(host);
        setting.setPort(port);
        Server server = new JdkHttpServer(setting);
        server.start();
        serverMap.put(serverName, server);

        log.info("├─ 启动服务器: {} -> {}:{} [{}]",
                highlight(serverName),
                host, port,
                success());
    }

    /**
     * 停止所有服务器
     */
    public void stopAll() {
        for (Map.Entry<String, Server> entry : serverMap.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("停止服务器: {} [{}]", entry.getKey(), success());
            } catch (Exception e) {
                log.error("停止服务器失败: {} [{}]", entry.getKey(), failed(), e);
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
    public Server getServer(String name) {
        return serverMap.get(name);
    }

    /**
     * 检查服务器是否运行中
     *
     * @param name 服务器名称
     * @return 是否运行中
     */
    public boolean isRunning(String name) {
        Server server = serverMap.get(name);
        return server != null && server.isRunning();
    }

    @Override
    public void close() {
        stopAll();
    }
}