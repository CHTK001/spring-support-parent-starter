package com.chua.starter.ai.support.configuration.protocol;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiManufacturerFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聚合协议服务管理器。
 *
 * <p>聚合 ChatClient 唯一实例，复用不随请求重建；负责从 {@code sys_ai_manufacturer} 表
 * 动态构建底层 ChatClient（每个启用厂商一个独立实例），并通过独立端口（{@code runtime}
 * 分组 {@code protocol_server_port} 配置）暴露 OpenAI / Claude / Gemini 三协议端点。</p>
 *
 * <p>数据库（厂商/模型）变化时，调用 {@link #reloadClients()} 对聚合客户端执行增删，支持热更新，
 * 无需重启服务。启停通过 {@code protocol_server_enabled} 配置或 {@link #start()}/{@link #stop()} 控制。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProtocolServerManager {

    /**
     * 聚合协议配置分组
     */
    private static final String GROUP = "runtime";

    /**
     * 服务端实现类型（jdk 内置 HTTP 服务）
     */
    private static final String SERVER_TYPE = "jdk";

    /**
     * 默认路由权重
     */
    private static final int DEFAULT_WEIGHT = 1;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 厂商门面服务（读取 sys_ai_manufacturer 表）
     */
    private final AiManufacturerFacadeService manufacturerFacadeService;

    /**
     * 全局 ChatClient 注册中心（聚合客户端注册为默认，供聊天/协议统一路由）
     */
    private final ChatClientRegistry chatClientRegistry;

    /**
     * 聚合 ChatClient（全局唯一，复用）
     */
    private AggregateChatClient aggregateClient;

    /**
     * 协议服务实例（独立端口）
     */
    private Server protocolServer;

    /**
     * 启动聚合协议服务。
     *
     * <p>幂等：已运行时直接返回。构建聚合客户端（从厂商表加载）、创建协议 Server 并绑定三协议路由。
     * 显式调用（接口/外部触发）不校验 {@code protocol_server_enabled}，强制启动。</p>
     */
    public synchronized void start() {
        if (protocolServer != null) {
            log.debug("[AiProtocolServer] 聚合协议服务已运行，忽略重复启动");
            return;
        }
        int port = settingReader.getInt(GROUP, "protocol_server_port", 8090);
        String host = settingReader.getStr(GROUP, "protocol_server_host");
        if (StringUtils.isBlank(host)) {
            host = "0.0.0.0";
        }
        try {
            // 构建聚合客户端（failover 策略，支持运行时 addClient 动态路由）并从厂商表加载底层客户端
            com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting setting =
                    new com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting();
            setting.setStrategy("failover");
            aggregateClient = new AggregateChatClient(setting);
            reloadClients();
            // 注册为全局聚合客户端，供聊天接口统一路由
            chatClientRegistry.setAggregate(aggregateClient);
            chatClientRegistry.setDefault(aggregateClient);

            // 创建协议 Server 并绑定三协议路由
            Server server = ServerBuilder.create()
                    .type(SERVER_TYPE)
                    .host(host)
                    .port(port)
                    .build();
            aggregateClient.bindServer(server);
            server.start();
            this.protocolServer = server;
            log.info("[AiProtocolServer] 聚合协议服务已启动: {}:{} ({} 个底层客户端)",
                    host, port, aggregateClient.clientCount());
        } catch (Exception e) {
            log.error("[AiProtocolServer] 启动聚合协议服务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 停止聚合协议服务。
     */
    public synchronized void stop() {
        if (protocolServer == null) {
            return;
        }
        try {
            aggregateClient.unbindServer(protocolServer);
            protocolServer.stop();
        } catch (Exception e) {
            log.warn("[AiProtocolServer] 停止聚合协议服务异常: {}", e.getMessage());
        } finally {
            this.protocolServer = null;
            log.info("[AiProtocolServer] 聚合协议服务已停止");
        }
    }

    /**
     * 从厂商表动态重载底层 ChatClient（热更新）。
     *
     * <p>按厂商编码对比：新增则 {@code addClient}，移除则 {@code removeClient}。</p>
     */
    public synchronized void reloadClients() {
        if (aggregateClient == null) {
            log.warn("[AiProtocolServer] 聚合客户端未初始化，跳过热更新");
            return;
        }
        List<SysAiManufacturer> manufacturers = manufacturerFacadeService.list();
        log.info("[AiProtocolServer] 厂商表共 {} 个厂商记录", manufacturers != null ? manufacturers.size() : 0);
        if (manufacturers == null || manufacturers.isEmpty()) {
            return;
        }
        int added = 0;
        for (SysAiManufacturer m : manufacturers) {
            if (m.getSysAiManufacturerStatus() == null || m.getSysAiManufacturerStatus() != 1) {
                continue;
            }
            String code = m.getSysAiManufacturerCode();
            if (StringUtils.isBlank(code)) {
                continue;
            }
            // 已存在则跳过（避免重复）
            boolean exists = aggregateClient.clients().stream()
                    .anyMatch(wc -> code.equals(wc.provider()));
            if (exists) {
                continue;
            }
            ChatClient client = buildChatClient(m);
            if (client == null) {
                continue;
            }
            aggregateClient.addClient(code, null, DEFAULT_WEIGHT, client);
            added++;
        }
        if (added > 0) {
            log.info("[AiProtocolServer] 聚合客户端热更新完成，新增 {} 个，当前共 {} 个", added, aggregateClient.clientCount());
        } else {
            log.debug("[AiProtocolServer] 聚合客户端无新增，当前共 {} 个", aggregateClient.clientCount());
        }
    }

    /**
     * 从厂商实体构建底层 ChatClient。
     *
     * @param manufacturer 厂商实体
     * @return ChatClient 实例；配置缺失时返回 null
     */
    private ChatClient buildChatClient(SysAiManufacturer manufacturer) {
        String provider = manufacturer.getSysAiManufacturerFactory();
        if (StringUtils.isBlank(provider)) {
            provider = manufacturer.getSysAiManufacturerCode();
        }
        String apiKey = manufacturer.getSysAiManufacturerAppKey();
        String baseUrl = manufacturer.getSysAiManufacturerBaseUrl();
        if (StringUtils.isBlank(provider) || StringUtils.isBlank(apiKey)) {
            log.debug("[AiProtocolServer] 厂商 {} 缺少 provider/apiKey，跳过", manufacturer.getSysAiManufacturerCode());
            return null;
        }
        try {
            ChatClient client = ChatClient.create(ChatClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .model(settingReader.getStr(GROUP, "default_model"))
                    .build());
            if (client == null) {
                log.warn("[AiProtocolServer] 厂商 {} ChatClient.create 返回 null (SPI 未发现 provider={})",
                        manufacturer.getSysAiManufacturerCode(), provider);
            }
            return client;
        } catch (Exception e) {
            log.warn("[AiProtocolServer] 构建厂商 {} ChatClient 失败: {}", provider, e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前聚合客户端。
     *
     * @return 聚合客户端；未启动时返回 null
     */
    public AggregateChatClient getAggregateClient() {
        return aggregateClient;
    }

    /**
     * 是否已运行。
     *
     * @return true 表示协议服务运行中
     */
    public boolean isRunning() {
        return protocolServer != null;
    }

    /**
     * 获取协议服务端口。
     *
     * @return 端口；未启动返回 0
     */
    public int getPort() {
        return protocolServer != null ? protocolServer.getPort() : 0;
    }

    /**
     * 应用就绪后按全局配置启动聚合协议服务（仅当 {@code protocol_server_enabled=true}）。
     *
     * <p>执行顺序在 {@code GlobalChatClientManager}（LOWEST_PRECEDENCE）之后，
     * 确保聚合客户端最终注册为默认客户端，不被覆盖。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
    public void onApplicationReady() {
        boolean enabled = settingReader.getBool(GROUP, "protocol_server_enabled", false);
        if (!enabled) {
            log.info("[AiProtocolServer] protocol_server_enabled=false，自动启动跳过（可显式调用 start）");
            return;
        }
        start();
    }

    /**
     * 根据当前全局配置刷新聚合协议服务（启停切换 / 客户端热更新）。
     *
     * <p>配置变更后调用：enabled 变化时启停服务，端口变化时重建，厂商表变化时增删客户端。</p>
     */
    public synchronized void refresh() {
        boolean enabled = settingReader.getBool(GROUP, "protocol_server_enabled", false);
        if (enabled) {
            if (protocolServer == null) {
                start();
            } else {
                reloadClients();
            }
        } else {
            stop();
        }
    }
}
