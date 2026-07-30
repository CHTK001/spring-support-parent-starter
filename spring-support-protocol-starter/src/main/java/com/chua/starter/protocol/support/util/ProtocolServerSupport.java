package com.chua.starter.protocol.support.util;

import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.network.protocol.ServerSetting;
import com.chua.common.support.network.protocol.server.ProtocolServer;

/**
 * ProtocolServer 统一增强封装。
 *
 * @author CH
 */
public final class ProtocolServerSupport {

    private ProtocolServerSupport() {
    }

    /**
     * 按类型创建协议服务器实例。
     *
     * @param type    协议 SPI 名称
     * @param setting 服务器配置
     * @return 协议服务器
     */
    public static ProtocolServer create(String type, ServerSetting setting) {
        if (setting == null) {
            throw new IllegalArgumentException("ServerSetting 不能为空");
        }
        String protocol = StringUtils.isBlank(type) ? "http" : type;
        return ProtocolServer.create(protocol, setting);
    }

    /**
     * 若未运行则启动服务器。
     *
     * @param server 协议服务器
     * @return 同一实例
     * @throws Exception 启动异常
     */
    public static ProtocolServer startIfNecessary(ProtocolServer server) throws Exception {
        if (server == null) {
            throw new IllegalArgumentException("ProtocolServer 不能为空");
        }
        if (!server.isRunning()) {
            server.start();
        }
        return server;
    }

    /**
     * 静默停止或关闭服务器。
     *
     * @param server 协议服务器
     */
    public static void stopQuietly(ProtocolServer server) {
        if (server == null) {
            return;
        }
        try {
            if (server.isRunning()) {
                server.stop();
            } else {
                server.close();
            }
        } catch (Exception ignored) {
        }
    }
}
