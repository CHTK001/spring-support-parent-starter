package com.chua.starter.server.support.spi;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.starter.server.support.entity.ServerHost;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 服务器协议 SPI 管理器。
 */
public class ServerHostProtocolManager {

    private final com.chua.common.support.core.spi.ServiceProvider<ServerHostProtocolSpi> provider =
            ServiceProvider.of(ServerHostProtocolSpi.class);

    /**
     * 标准化协议名称，缺省回落到 local。
     */
    public String normalizeType(String serverType) {
        return StringUtils.hasText(serverType)
                ? serverType.trim().toUpperCase(Locale.ROOT)
                : "LOCAL";
    }

    /**
     * 判断目标主机是否为本机协议。
     */
    public boolean isLocal(ServerHost host) {
        return resolve(host).isLocal();
    }

    /**
     * 判断目标主机是否应按 Windows 路径与命令语义处理。
     */
    public boolean isWindows(ServerHost host) {
        return resolve(host).isWindows(host);
    }

    /**
     * 解析协议默认主机地址。
     */
    public String resolveHost(ServerHost host) {
        if (host != null && StringUtils.hasText(host.getHost())) {
            return host.getHost().trim();
        }
        return resolve(host).getDefaultHost(host);
    }

    /**
     * 解析协议默认端口。
     */
    public Integer resolvePort(ServerHost host) {
        if (host != null && host.getPort() != null) {
            return host.getPort();
        }
        return resolve(host).getDefaultPort();
    }

    /**
     * 解析协议默认基础目录。
     */
    public String resolveBaseDirectory(ServerHost host) {
        if (host != null && StringUtils.hasText(host.getBaseDirectory())) {
            return host.getBaseDirectory().trim();
        }
        return resolve(host).getDefaultBaseDirectory(host);
    }

    /**
     * 创建协议对应的文件客户端。
     */
    public FileClient createFileClient(ServerHost host) {
        return resolve(host).createFileClient(host);
    }

    /**
     * 获取协议 SPI，缺省回落到 local。
     */
    private ServerHostProtocolSpi resolve(ServerHost host) {
        String type = host == null ? "local" : normalizeType(host.getServerType()).toLowerCase(Locale.ROOT);
        ServerHostProtocolSpi protocol = provider.getExtension(type);
        return protocol != null ? protocol : provider.getExtension("local");
    }
}
