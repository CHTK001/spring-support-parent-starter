package com.chua.starter.account.spi;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.VideoGenerationResult;
import com.chua.common.support.spi.ServiceProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文生视频渠道 SPI —— 把「具备 {@code ChatClient#generateVideo} 能力」的实现登记成渠道。
 *
 * <p>与 {@link ImageChannelProvider} 平行：免登录、无持久化连接，区别只在产出物
 * （视频 vs 图片）与参数（镜头运动、超时更长）。需要登录态的渠道（如豆包 / 通义）
 * 由请求体临时携带 Cookie / Token，本次调用结束即释放客户端。</p>
 *
 * <p>新增一个文生视频渠道：</p>
 * <ol>
 *   <li>底层 {@code ChatClient}（或本接口实现自身）已能产出视频；</li>
 *   <li>写一个本接口实现并在
 *       {@code META-INF/extensions/com.chua.starter.account.spi.VideoChannelProvider} 登记。</li>
 * </ol>
 *
 * <p>渠道种类固定为 {@link ChannelKind#VIDEO}，账号类型为空集合。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface VideoChannelProvider extends ChannelProvider {

    /**
     * 单次请求的最大视频条数。
     *
     * <p>视频生成耗时与费用远高于图片，统一收敛为 1 条：前端不提供批量选项，
     * API 边界也不接受放大请求。</p>
     */
    int MAX_VIDEOS_PER_CALL = 1;

    /**
     * 默认超时时间（秒）。
     */
    // [P3C A20 豁免]
    int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 底层 {@code ChatClient} SPI 扩展名（如 {@code doubao-proxy}）。
     *
     * <p>为 {@code null} 表示该渠道不依赖 ChatClient 扩展，实现必须自行覆盖
     * {@link #generate}。</p>
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
     * 获取底层对话客户端（文生视频复用 {@link ChatClient#generateVideo}）。
     *
     * @return 对话客户端
     * @throws IllegalStateException 未声明扩展名或扩展未注册时抛出
     */
    default ChatClient chatClient() {
        return chatClient(setting());
    }

    /**
     * 按指定配置获取底层对话客户端（如页面填了 Cookie，可临时覆盖默认配置）。
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
     * 渠道支持的视频模型（供渠道管理页展示，可为空）。
     *
     * @return 模型列表
     */
    default List<Map<String, Object>> listModels() {
        return List.of();
    }

    /**
     * 生成视频。
     *
     * <p>默认委托 {@link ChatClient#generateVideo}；不依赖 ChatClient 的实现覆盖本方法即可。</p>
     *
     * @param prompt         提示词
     * @param ratio          宽高比（如 {@code 16:9}），可为空
     * @param cameraMovement 镜头运动描述，可为空
     * @param refImageKey    参考图标识，可为空
     * @param timeoutSeconds 超时秒数
     * @param model          视频模型；为空时用渠道默认模型
     * @return 生成结果
     */
    default VideoGenerationResult generate(String prompt, String ratio, String cameraMovement,
            String refImageKey, int timeoutSeconds, String model) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        ChatClient client = chatClient();
        try {
            if (model != null && !model.isBlank()) {
                client.model(model.trim());
            }
            int timeout = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
            return client.generateVideo(prompt, ratio, cameraMovement, refImageKey, timeout);
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * 生成视频（不带模型参数的便捷重载）。
     *
     * @param prompt         提示词
     * @param ratio          宽高比
     * @param cameraMovement 镜头运动
     * @param refImageKey    参考图标识
     * @param timeoutSeconds 超时秒数
     * @return 生成结果
     */
    default VideoGenerationResult generate(String prompt, String ratio, String cameraMovement,
            String refImageKey, int timeoutSeconds) {
        return generate(prompt, ratio, cameraMovement, refImageKey, timeoutSeconds, null);
    }

    /**
     * 文生视频渠道固定为 {@link ChannelKind#VIDEO}。
     *
     * @return {@link ChannelKind#VIDEO}
     */
    @Override
    default ChannelKind kind() {
        return ChannelKind.VIDEO;
    }

    /**
     * 文生视频渠道没有账号概念。
     *
     * @return 空集合
     */
    @Override
    default Set<AccountType> accountTypes() {
        return Set.of();
    }

    /**
     * 文生视频渠道无需绑定。
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
