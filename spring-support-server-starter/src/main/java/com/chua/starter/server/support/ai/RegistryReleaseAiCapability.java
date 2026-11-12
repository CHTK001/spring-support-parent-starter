package com.chua.starter.server.support.ai;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.ai.support.registry.ChatClientRegistry;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 基于 ai-starter 注册中心的实现。
 *
 * <p>只在 {@link ReleaseAiConfiguration} 里实例化，因此宿主没装 ai-starter 时
 * 这个类不会被加载，也就不会因缺类启动失败。</p>
 *
 * <p>客户端选择顺序：默认客户端优先（UI 里通常配的是默认模型），
 * 拿不到再取注册表里的第一个——只要求「能对话」，不在这里做模型路由。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public class RegistryReleaseAiCapability implements ReleaseAiCapability {

    /**
     * 对话客户端注册中心
     */
    private final ChatClientRegistry registry;

    /**
     * 构造
     *
     * @param registry 注册中心
     */
    public RegistryReleaseAiCapability(ChatClientRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean available() {
        return client() != null;
    }

    @Override
    public Set<String> providers() {
        try {
            return new LinkedHashSet<>(registry.getProviders());
        } catch (Exception e) {
            return Set.of();
        }
    }

    @Override
    public Set<String> models() {
        try {
            return new LinkedHashSet<>(registry.getModels());
        } catch (Exception e) {
            return Set.of();
        }
    }

    @Override
    public String chat(String prompt) {
        ChatClient client = client();
        if (client == null) {
            throw new IllegalStateException("没有可用的 AI 对话客户端");
        }
        return client.chatSync(prompt);
    }

    /**
     * 取一个可用客户端：默认优先，其次注册表第一个。
     *
     * @return 客户端；都没有返回 null
     */
    private ChatClient client() {
        ChatClient def = registry.getDefault();
        if (def != null) {
            return def;
        }
        for (ChatClient c : registry.getAll()) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }
}
