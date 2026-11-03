package com.chua.starter.channel.spi;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.spi.ServiceProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ChatClient 渠道 SPI —— 把任意 {@code ChatClient} SPI 扩展登记成「渠道管理」里的一条渠道。
 *
 * <p>与 {@code AccountChannelAuthManager}（固定账号 + OAuth 绑定）和
 * {@link TemporaryAccountProvider}（临时身份）并列：本接口面向<b>免登录</b>的
 * {@code ChatClient} 实现（如 DuckDuckGo {@code duck.ai}），不产生连接行、不落凭据，
 * 每次调用按需新建客户端并按 {@link ChatClient#close()} 释放资源。</p>
 *
 * <p>新增一个 ChatClient 渠道只需两步：</p>
 * <ol>
 *   <li>确认 {@code ChatClient} SPI 已注册扩展名（{@code @Spi("duckduckgo")}）；</li>
 *   <li>写一个本接口实现，{@link #extension()} 返回该扩展名，并在
 *       {@code META-INF/extensions/com.chua.starter.channel.spi.ChatChannelProvider} 登记。</li>
 * </ol>
 *
 * <p>渠道种类固定为 {@link ChannelKind#CHAT}，账号类型为空集合（没有绑定 / 临时身份语义）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ChatChannelProvider extends ChannelProvider {

    /**
     * 底层 {@code ChatClient} SPI 扩展名（如 {@code duckduckgo}）。
     *
     * @return 扩展名
     */
    String extension();

    /**
     * 新建底层对话客户端时使用的配置（渠道实现可覆盖以注入 baseUrl / 超时等）。
     *
     * @return 客户端配置
     */
    default ChatClientSetting setting() {
        return ChatClientSetting.builder()
                .provider(extension())
                .build();
    }

    /**
     * 新建一个底层对话客户端（调用方负责 {@link ChatClient#close()}）。
     *
     * @return 对话客户端
     * @throws IllegalStateException 对应 ChatClient 扩展未注册时抛出
     */
    default ChatClient newClient() {
        ChatClient client = ServiceProvider.of(ChatClient.class).getNewExtension(extension(), setting());
        if (client == null) {
            throw new IllegalStateException("未注册的 ChatClient 扩展: " + extension());
        }
        return client;
    }

    /**
     * 渠道内置模型清单（供渠道管理页展示）。
     *
     * <p>默认取 {@link ChatClient#models()}：多数免登录实现是静态模型表，
     * 不触达上游、不启动浏览器；创建出来的客户端在本方法内即关闭。</p>
     *
     * @return 模型列表（每项含 {@code id} / {@code name} / 可选 {@code contextWindow}）
     */
    default List<Map<String, Object>> listModels() {
        ChatClient client = newClient();
        try {
            List<ModelDefinition> definitions = client.models();
            if (definitions == null || definitions.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> models = new ArrayList<>(definitions.size());
            for (ModelDefinition definition : definitions) {
                if (definition == null || definition.getId() == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>(3);
                item.put("id", definition.getId());
                item.put("name", definition.getName());
                item.put("contextWindow", definition.getContextWindowTokens());
                models.add(item);
            }
            return models;
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * 执行一次同步对话（渠道管理页的「试对话」入口使用）。
     *
     * @param prompt 用户输入
     * @param model  模型标识；为空时用渠道默认模型
     * @param system 系统提示词；可为空
     * @return 完整回答文本
     * @throws Exception 上游调用失败
     */
    default String chat(String prompt, String model, String system) throws Exception {
        ChatClient client = newClient();
        try {
            if (model != null && !model.isBlank()) {
                client.model(model);
            }
            if (system != null && !system.isBlank()) {
                client.system(system);
            }
            return client.chatSync(prompt);
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * 免登录对话渠道固定为 {@link ChannelKind#CHAT}。
     *
     * @return {@link ChannelKind#CHAT}
     */
    @Override
    default ChannelKind kind() {
        return ChannelKind.CHAT;
    }

    /**
     * 对话渠道没有账号概念。
     *
     * @return 空集合
     */
    @Override
    default Set<AccountType> accountTypes() {
        return Set.of();
    }

    /**
     * 对话渠道无需绑定，接入方式恒为 {@code none}。
     *
     * @return {@code none}
     */
    @Override
    default String authMethod() {
        return "none";
    }

    /**
     * 静默关闭客户端（关闭失败不影响主流程）。
     *
     * @param client 对话客户端
     */
    private static void closeQuietly(ChatClient client) {
        try {
            client.close();
        } catch (Exception ignored) {
            // 释放资源失败不改变调用结果
        }
    }
}
