package com.chua.starter.server.support.service.impl;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.configuration.AiProviderDefaults;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.properties.ProviderProperties;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.model.ServerCapabilityView;
import com.chua.starter.server.support.service.ServerCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerCapabilityServiceImpl implements ServerCapabilityService {

    private final ServerManagementProperties properties;
    private final ApplicationContext applicationContext;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectProvider<AiProperties> aiPropertiesProvider;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    @Override
    public ServerCapabilityView getCapabilities() {
        AiCapabilityState aiCapabilityState = resolveAiCapabilityState();
        return ServerCapabilityView.builder()
                .aiEnabled(aiCapabilityState.enabled())
                .softEnabled(hasBean("com.chua.starter.soft.support.service.SoftManagementService"))
                .remoteGatewayEnabled(properties.getRemoteGateway().isEnable())
                .fileWatchEnabled(properties.getFileWatch().isEnable()
                        && hasBean("com.chua.starter.server.support.service.ServerFileWatchService"))
                .socketEnabled(socketSessionTemplateProvider.getIfAvailable() != null)
                .serviceAutoDetectEnabled(properties.getServiceOperation().isAutoDetectEnabled())
                .aiProvider(aiCapabilityState.provider())
                .aiDefaultProvider(aiCapabilityState.defaultProvider())
                .aiProviderCount(aiCapabilityState.providerCount())
                .aiProviderNames(aiCapabilityState.providerNames())
                .aiConfigReady(aiCapabilityState.configReady())
                .aiChatClientReady(aiCapabilityState.chatClientReady())
                .aiStatusText(aiCapabilityState.statusText())
                .aiUnavailableReason(aiCapabilityState.unavailableReason())
                .aiUnavailableCode(aiCapabilityState.unavailableCode())
                .aiProviderResolvedFrom(aiCapabilityState.providerResolvedFrom())
                .build();
    }

    private AiCapabilityState resolveAiCapabilityState() {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        AiProperties properties = AiProviderDefaults.normalize(aiPropertiesProvider.getIfAvailable());
        boolean chatClientReady = chatClient != null;
        int providerCount = properties.getProviders() == null ? 0 : properties.getProviders().size();
        java.util.List<String> providerNames = properties.getProviders() == null
                ? java.util.List.of()
                : properties.getProviders().keySet().stream().sorted().toList();
        if (providerCount <= 0 && chatClientReady && StringUtils.hasText(chatClient.getProvider())) {
            return new AiCapabilityState(true, chatClient.getProvider(), properties.getDefaultProvider(), 1,
                    java.util.List.of(chatClient.getProvider()), true, true, "已激活", null, null, "chat-client");
        }
        if (providerCount <= 0) {
            return new AiCapabilityState(false, null, properties.getDefaultProvider(), 0, providerNames, false, chatClientReady, "未激活", "未配置任何 AI Provider", "NO_PROVIDER", null);
        }
        String defaultProvider = properties.getDefaultProvider();
        ProviderResolution providerResolution = resolveProvider(chatClient, properties);
        String provider = providerResolution.provider();
        if (!StringUtils.hasText(provider)) {
            return new AiCapabilityState(false, null, defaultProvider, providerCount, providerNames, false, chatClientReady, "未激活", "未设置默认 AI Provider", "NO_DEFAULT_PROVIDER", providerResolution.source());
        }
        ProviderProperties config = properties.getProviders().get(provider);
        if (config == null) {
            if (chatClientReady) {
                return new AiCapabilityState(true, provider, defaultProvider, providerCount, providerNames, true, true, "已激活", null, null, providerResolution.source());
            }
            return new AiCapabilityState(false, provider, defaultProvider, providerCount, providerNames, false, chatClientReady, "未激活", "默认 Provider 未找到可用配置", "PROVIDER_NOT_FOUND", providerResolution.source());
        }
        if (!StringUtils.hasText(config.getApiKey()) && !StringUtils.hasText(config.getAppKey())) {
            if (chatClientReady) {
                return new AiCapabilityState(true, provider, defaultProvider, providerCount, providerNames, true, true, "已激活", null, null, providerResolution.source());
            }
            return new AiCapabilityState(false, provider, defaultProvider, providerCount, providerNames, false, chatClientReady, "未激活", "Provider 缺少 apiKey/appKey", "MISSING_CREDENTIAL", providerResolution.source());
        }
        if (chatClient == null) {
            return new AiCapabilityState(false, provider, defaultProvider, providerCount, providerNames, true, false, "未激活", "AI 配置存在，但 ChatClient 未装配", "CHAT_CLIENT_MISSING", providerResolution.source());
        }
        return new AiCapabilityState(true, provider, defaultProvider, providerCount, providerNames, true, true, "已激活", null, null, providerResolution.source());
    }

    private ProviderResolution resolveProvider(ChatClient chatClient, AiProperties properties) {
        if (chatClient != null && StringUtils.hasText(chatClient.getProvider())) {
            return new ProviderResolution(chatClient.getProvider(), "chat-client");
        }
        if (properties != null && StringUtils.hasText(properties.getDefaultProvider())) {
            return new ProviderResolution(properties.getDefaultProvider(), "default-provider");
        }
        if (properties != null && properties.getProviders() != null && properties.getProviders().size() == 1) {
            return new ProviderResolution(properties.getProviders().keySet().iterator().next(), "single-provider");
        }
        return new ProviderResolution(null, null);
    }

    private boolean hasBean(String className) {
        try {
            Class<?> type = Class.forName(className);
            return !applicationContext.getBeansOfType(type).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record AiCapabilityState(
            boolean enabled,
            String provider,
            String defaultProvider,
            int providerCount,
            java.util.List<String> providerNames,
            boolean configReady,
            boolean chatClientReady,
            String statusText,
            String unavailableReason,
            String unavailableCode,
            String providerResolvedFrom
    ) {
    }

    private record ProviderResolution(
            String provider,
            String source
    ) {
    }
}
