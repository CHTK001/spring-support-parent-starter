package com.chua.starter.service.utils;

import com.chua.common.support.core.utils.StringUtils;
import com.chua.common.support.network.protocol.ServerSetting;
import com.chua.common.support.network.protocol.server.ProtocolServer;

/**
 * ProtocolServer 统一增强封装。
 */
public final class ProtocolServerSupport {

    private ProtocolServerSupport() {
    }

    public static ProtocolServer create(String type, ServerSetting setting) {
        if (setting == null) {
            throw new IllegalArgumentException("ServerSetting 不能为空");
        }
        String protocol = StringUtils.isBlank(type) ? "http" : type;
        return ProtocolServer.create(protocol, setting);
    }

    public static ProtocolServer startIfNecessary(ProtocolServer server) throws Exception {
        if (server == null) {
            throw new IllegalArgumentException("ProtocolServer 不能为空");
        }
        if (!server.isRunning()) {
            server.start();
        }
        return server;
    }

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
