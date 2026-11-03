package com.chua.starter.channel.spi;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.common.support.spi.ServiceProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文生图渠道 SPI —— 把「具备 {@code ChatClient#generateImage} 能力」的实现登记成渠道。
 *
 * <p>与 {@link ChatChannelProvider} 平行：同样免登录、无凭据、无水久化连接，
 * 区别只在产出物（图片 vs 文本）与前端面板（参数、画廊 vs 对话）。</p>
 *
 * <p>新增一个文生图渠道：</p>
 * <ol>
 *   <li>底层 {@code ChatClient}（或本接口实现自身）已能产出图片；</li>
 *   <li>写一个本接口实现并在
 *       {@code META-INF/extensions/com.chua.starter.channel.spi.ImageChannelProvider} 登记。</li>
 * </ol>
 *
 * <p>渠道种类固定为 {@link ChannelKind#IMAGE}，账号类型为空集合。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ImageChannelProvider extends ChannelProvider {

    /**
     * 底层 {@code ChatClient} SPI 扩展名（如 {@code trae}）。
     *
     * <p>为 {@code null} 表示该渠道不依赖 ChatClient 扩展（例如纯 URL 拼装的免登录图床），
     * 此时实现必须自行覆盖 {@link #generate}。</p>
     *
     * @return 扩展名，可为 {@code null}
     */
    String extension();

    /**
     * 新建底层客户端时使用的配置。
     *
     * @return 客户端配置
     */
    default ChatClientSetting setting() {
        return ChatClientSetting.builder()
                .provider(extension())
                .build();
    }

    /**
     * 获取底层对话客户端（文生图复用 {@link ChatClient#generateImage}）。
     *
     * @return 对话客户端
     * @throws IllegalStateException 未声明扩展名或扩展未注册时抛出
     */
    default ChatClient chatClient() {
        return chatClient(setting());
    }

    /**
     * 按指定配置获取底层对话客户端（如页面填了 JWT，可临时覆盖默认配置）。
     *
     * @param setting 客户端配置
     * @return 对话客户端
     * @throws IllegalStateException 未声明扩展名或扩展未注册时抛出
     */
    default ChatClient chatClient(ChatClientSetting setting) {
        String extension = extension();
        if (extension == null || extension.isBlank()) {
            throw new IllegalStateException("渠道 " + channel() + " 不依赖 ChatClient 扩展，需自行实现 generate");
        }
        ChatClient client = ServiceProvider.of(ChatClient.class).getNewExtension(extension, setting);
        if (client == null) {
            throw new IllegalStateException("未注册的 ChatClient 扩展: " + extension);
        }
        return client;
    }

    /**
     * 渠道支持的出图模型 / 风格（供渠道管理页展示，可为空）。
     *
     * @return 模型列表
     */
    default List<Map<String, Object>> listModels() {
        return List.of();
    }

    /**
     * 生成图片。
     *
     * <p>默认委托 {@link ChatClient#generateImage}；不依赖 ChatClient 的渠道（如直连图床 URL）
     * 覆盖本方法即可，无需引入扩展。</p>
     *
     * @param prompt 提示词
     * @param ratio  宽高比（如 {@code 1:1}），可为空
     * @param n      张数，至少 1
     * @param width  宽度（0 表示按 ratio）
     * @param height 高度（0 表示按 ratio）
     * @param model  出图模型；为空时用渠道默认模型
     * @return 生成结果
     */
    default ImageGenerationResult generate(String prompt, String ratio, int n, int width, int height, String model) {
        ChatClient client = chatClient();
        try {
            return client.generateImage(prompt, ratio, Math.max(1, n), width, height, null, null);
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * 生成图片（不带模型参数的便捷重载）。
     *
     * @param prompt 提示词
     * @param ratio  宽高比
     * @param n      张数
     * @param width  宽度
     * @param height 高度
     * @return 生成结果
     */
    default ImageGenerationResult generate(String prompt, String ratio, int n, int width, int height) {
        return generate(prompt, ratio, n, width, height, null);
    }

    /**
     * 文生图渠道固定为 {@link ChannelKind#IMAGE}。
     *
     * @return {@link ChannelKind#IMAGE}
     */
    @Override
    default ChannelKind kind() {
        return ChannelKind.IMAGE;
    }

    /**
     * 文生图渠道没有账号概念。
     *
     * @return 空集合
     */
    @Override
    default Set<AccountType> accountTypes() {
        return Set.of();
    }

    /**
     * 文生图渠道无需绑定。
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
