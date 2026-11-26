package com.chua.starter.account.channel.service;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.aggregate.MultiKeyChatClient;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.cooldown.Backoff;
import com.chua.common.support.task.cooldown.CooldownPolicies;
import com.chua.common.support.task.cooldown.CooldownPolicy;
import com.chua.starter.account.channel.free.FreeChannelChatClient;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.account.channel.support.ProviderCredentials;
import com.chua.starter.account.channel.support.ProviderRouteTargets;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 渠道对话客户端唯一装配入口，负责可用性、账号池、认证、协议端点与限流配置。
 * 上层只传渠道及模型标识，拿到独立客户端后负责关闭。
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class AccountChannelChatClientService {
    /** 渠道目录。 */
    private final AccountChannelService channels;
    /** 模型目录。 */
    private final AccountChannelModelService models;
    /** 账号作用域。 */
    private final AccountChannelConnectionService connections;
    /** 已持久化的连接健康。 */
    private final AccountChannelHealthService health;

    /**
     * 创建独立客户端，所有凭据处理留在账号模块。
     * @param providerId 渠道
     * @param model 模型
     * @param ownerScope 调用身份作用域
     * @param dynamicRoutes 上层路由扩展
     * @return 独立客户端
     */
    public ChatClient create(String providerId, String model, String ownerScope, Map<String, RouteTarget> dynamicRoutes) {
        channels.requireAvailableModel(providerId, model);
        SysAccountChannel channel = channels.require(providerId);
        Map<String, Object> extra = channel.getExtra() == null ? Map.of() : channel.getExtra();
        String platform = channel.getPlatform();
        String extension = platform == null ? ChatClient.OPENAI_EXTENSION : platform.trim();
        if (extension.toLowerCase(Locale.ROOT).startsWith("chat-")) extension = extension.substring(5);
        if ("oauth".equalsIgnoreCase(channel.getAuthScheme())) {
            return oauthClient(channel, model, ownerScope);
        }
        CooldownPolicy policy = new CooldownPolicy(number(extra, "rate_limit_base_ms"), number(extra, "rate_limit_max_ms"),
                text(extra, "rate_limit_backoff") == null ? null : Backoff.of(text(extra, "rate_limit_backoff")));
        CooldownPolicies.register(platform + "|", policy);
        CooldownPolicies.register(providerId + "|", policy);
        LinkedHashSet<String> allKeys = new LinkedHashSet<>(ProviderCredentials.apiKeys(channel.getCredentials()));
        for (SysAccountChannelConnection connection : connections.listPoolForScope(providerId, ownerScope)) {
            if (available(connection)) allKeys.addAll(ProviderCredentials.apiKeys(connection.getCredentials()));
        }
        List<String> keys = new ArrayList<>(allKeys);
        int maximum = (int) number(extra, "max_accounts");
        if (maximum > 0 && keys.size() > maximum) keys = new ArrayList<>(keys.subList(0, maximum));
        boolean free = ProviderRouteTargets.isFreeChannel(platform);
        if (keys.isEmpty()) {
            if (!free && !"none".equalsIgnoreCase(channel.getAuthScheme()) && (platform == null || !platform.toLowerCase(Locale.ROOT).startsWith("chat-"))) {
                throw new IllegalStateException("渠道没有可用凭据: " + providerId);
            }
            keys.add(null);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        map(extra.get("custom_headers")).forEach((key, value) -> headers.put(key, String.valueOf(value)));
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            for (String name : strings(extra.get("passthrough_request_headers"))) {
                String value = attributes.getRequest().getHeader(name);
                if (value != null && !value.isBlank()) headers.putIfAbsent(name, value);
            }
        }
        Map<String, RouteTarget> routes = dynamicRoutes == null ? ProviderRouteTargets.fromExtra(extra) : dynamicRoutes;
        Map<String, ChatClient> clients = new LinkedHashMap<>();
        try {
            for (String key : keys) {
                ChatClientSetting setting = ChatClientSetting.builder().provider(extension).model(model).appKey(key)
                        .baseUrl(endpoint(channel, model)).authScheme(channel.getAuthScheme())
                        .authHeader(text(extra, "auth_header")).authPrefix(text(extra, "auth_prefix"))
                        .metricsUrl(text(extra, "metrics_url")).proxy(channel.getProxy()).extraHeaders(headers)
                        .passthroughResponseHeaders(strings(extra.get("passthrough_response_headers"))).routes(routes).build();
                ChatClient client = ChatClient.create(setting);
                if (client == null) throw new IllegalStateException("渠道客户端不可用: " + extension);
                clients.put(key == null ? "" : key, client.model(model).extraBody(map(extra.get("extra_body"))));
            }
            return MultiKeyChatClient.multiKey(providerId, model, MultiKeyChatClient.Strategy.of(text(extra, "load_strategy")),
                    !"false".equalsIgnoreCase(text(extra, "auto_switch_on_rate_limit")), clients);
        } catch (RuntimeException failure) {
            clients.values().forEach(ChatClient::close);
            throw failure;
        }
    }

    /**
     * OAuth 协议目标由渠道 SPI 提供，经统一 FreeChannelChatClient 消费。
     * @param channel 渠道
     * @param model 模型
     * @param scope 作用域
     * @return 独立客户端
     */
    private ChatClient oauthClient(SysAccountChannel channel, String model, String scope) {
        RouteTarget target = oauthTarget(channel, scope);
        if (target == null) throw new IllegalStateException("渠道没有可用连接: " + channel.getProviderId());
        FreeChannelChatClient client = new FreeChannelChatClient(ChatClientSetting.builder().model(model).build());
        client.register(model, target);
        return client;
    }

    /**
     * 查询当前作用域可供给的 OAuth 模型路由，凭据转换由账号模块完成。
     * @param scope 账号作用域
     * @return 模型及渠道前缀路由
     */
    public Map<String, RouteTarget> routes(String scope) {
        Map<String, RouteTarget> result = new LinkedHashMap<>();
        for (SysAccountChannel channel : channels.listEnabledByAuthScheme("oauth")) {
            RouteTarget target = oauthTarget(channel, scope);
            if (target == null) continue;
            result.put(channel.getPlatform(), target);
            for (SysAccountChannelModel row : models.listEnabled(channel.getProviderId())) result.put(row.getModel(), target);
        }
        return result;
    }

    /** @param channel 渠道 @param scope 账号作用域 @return 聚合认证目标，无连接返回 null */
    private RouteTarget oauthTarget(SysAccountChannel channel, String scope) {
        AccountChannelAuthManager spi = ServiceProvider.of(AccountChannelAuthManager.class)
                .getExtension(channel.getPlatform().toLowerCase(Locale.ROOT));
        if (spi == null) throw new IllegalStateException("渠道认证实现不可用");
        List<String> keys = new ArrayList<>();
        RouteTarget template = null;
        for (SysAccountChannelConnection connection : connections.listPoolForScope(channel.getProviderId(), scope)) {
            if (!available(connection)) continue;
            RouteTarget route = spi.toRouteTarget(connection.getCredentials());
            if (route == null || (route.getApiKey() == null && !"none".equalsIgnoreCase(route.getAuthScheme()))) continue;
            if (template == null) template = route;
            keys.add(route.getApiKey() == null ? "" : route.getApiKey());
        }
        return template == null ? null : template.toBuilder().apiKeys(keys).build();
    }

    /** @param connection 连接 @return 是否未下线 */
    private boolean available(SysAccountChannelConnection connection) {
        var state = health.find(connection.getConnectionId());
        if (state == null) return true;
        if ("OPEN".equalsIgnoreCase(state.getCircuitState())) return false;
        if ("CLOSED".equalsIgnoreCase(state.getCircuitState())) return true;
        return !"DOWN".equals(state.getState());
    }

    /** @param channel 渠道 @param model 模型 @return 协议端点 */
    private String endpoint(SysAccountChannel channel, String model) {
        SysAccountChannelModel row = models.find(channel.getProviderId(), model);
        Map<String, Object> endpoints = map(channel.getExtra() == null ? null : channel.getExtra().get("endpoints"));
        if (row != null && row.getCapabilities() != null) for (Map<String, Object> capability : row.getCapabilities()) {
            if (capability == null) continue;
            String url = text(endpoints, String.valueOf(capability.get("protocol")));
            if (url != null && !url.isBlank()) return url;
        }
        return channel.getBaseUrl();
    }
    /** @param raw 原值 @return 字符串键映射 */
    private static Map<String, Object> map(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> values) values.forEach((key, value) -> {if (key != null && value != null) result.put(String.valueOf(key), value);});
        return result;
    }
    /** @param values 值 @param key 键 @return 文本 */
    private static String text(Map<String, Object> values, String key) {return values.get(key) == null ? null : String.valueOf(values.get(key));}
    /** @param values 值 @param key 键 @return 数值 */
    private static long number(Map<String, Object> values, String key) {
        try {return Long.parseLong(String.valueOf(values.get(key)));} catch (NumberFormatException ignored) {return 0;}
    }
    /** @param raw 值 @return 文本清单 */
    private static List<String> strings(Object raw) {
        if (raw == null) return List.of();
        return (raw instanceof List<?> list ? list.stream().map(String::valueOf) : java.util.Arrays.stream(String.valueOf(raw).split("[,\\n]")))
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }
}
