package com.chua.starter.sync.support;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.sync.impl.WebSocketSyncServer;
import com.chua.common.support.network.sync.impl.WebSocketSyncClient;

/**
 * SyncApplication 独立测试启动类。
 * <p>
 * 直接基于 common-starter 的 WebSocketSyncServer / WebSocketSyncClient
 * 启动双端通信，验证功能正确性。
 * </p>
 *
 * @since 2026/07/31
 * @author CH
 */
public class SyncApplication {

    public static void main(String[] args) throws Exception {
        System.out.println("=== SyncApplication 启动测试 ===");

        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19999);

        WebSocketSyncServer server = new WebSocketSyncServer(setting);
        server.start();
        System.out.println("服务端运行状态: " + server.isRunning());
        System.out.println("当前连接数: " + server.getConnectedClients().size());

        WebSocketSyncClient client = new WebSocketSyncClient("ws://127.0.0.1:19999");
        client.connect();
        System.out.println("客户端已连接: " + client.getClientId());

        Thread.sleep(500);

        System.out.println("连接数断言通过: " + (server.getConnectedClients().size() == 1));

        client.disconnect();
        server.stop();
        System.out.println("服务端已停止, 运行状态: " + server.isRunning());
        System.out.println("=== SyncApplication 测试完成 ===");
    }
}
