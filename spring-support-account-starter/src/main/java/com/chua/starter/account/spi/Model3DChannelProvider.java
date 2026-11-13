package com.chua.starter.account.spi;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.Model3DGenerationResult;
import com.chua.common.support.ai.generation.Model3DGenerator;
import com.chua.common.support.spi.ServiceProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图生 3D 渠道 SPI —— 把「具备 {@code ChatClient#generate3D} 能力」的实现登记成渠道。
 *
 * <p>输入是一张参考图（可选叠加提示词），输出是可下载的 3D 模型文件地址。
 * 与文生图 / 文生视频渠道一样属于免登录能力渠道：需要凭据的实现由请求体临时携带，
 * 不写入渠道配置、不做持久化连接。</p>
 *
 * <p>新增一个图生 3D 渠道：</p>
 * <ol>
 *   <li>底层 {@code ChatClient}（或本接口实现自身）已能产出 3D 模型；</li>
 *   <li>写一个本接口实现并在
 *       {@code META-INF/extensions/com.chua.starter.account.spi.Model3DChannelProvider} 登记。</li>
 * </ol>
 *
 * <p>渠道种类固定为 {@link ChannelKind#MODEL_3D}，账号类型为空集合。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface Model3DChannelProvider extends ChannelProvider {

    /**
     * 默认超时时间（秒）。
     *
     * <p>3D 重建通常比出图慢一个量级，默认给 5 分钟。</p>
     */
    // [P3C A20 豁免]
    int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 底层 {@code ChatClient} SPI 扩展名。
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
     * 获取底层对话客户端（图生 3D 复用 {@link ChatClient#generate3D}）。
     *
     * @return 对话客户端
     * @throws IllegalStateException 未声明扩展名或扩展未注册时抛出
     */
    default ChatClient chatClient() {
        return chatClient(setting());
    }

    /**
     * 按指定配置获取底层对话客户端。
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
     * 渠道支持的 3D 模型 / 格式（供渠道管理页展示，可为空）。
     *
     * @return 模型列表
     */
    default List<Map<String, Object>> listModels() {
        return List.of();
    }

    /**
     * 生成 3D 模型。
     *
     * <p>默认委托 {@link ChatClient#generate3D}；不依赖 ChatClient 的实现覆盖本方法即可。</p>
     *
     * @param prompt         提示词（纯图生 3D 时可为空）
     * @param refImageUrl    参考图 URL（图生 3D 必需）
     * @param format         目标格式（glb / gltf / obj / usdz），为空时由实现决定
     * @param timeoutSeconds 超时秒数
     * @param model          3D 模型标识；为空时用渠道默认模型
     * @return 生成结果
     */
    default Model3DGenerationResult generate(String prompt, String refImageUrl, String format,
            int timeoutSeconds, String model) {
        if (refImageUrl == null || refImageUrl.isBlank()) {
            throw new IllegalArgumentException("图生 3D 必须提供 refImageUrl");
        }
        ChatClient client = chatClient();
        try {
            if (model != null && !model.isBlank()) {
                client.model(model.trim());
            }
            int timeout = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
            if (client instanceof Model3DGenerator generator) {
                return generator.generate3D(prompt, refImageUrl, format, timeout);
            }
            throw new IllegalStateException("ChatClient 扩展 " + extension() + " 未实现 Model3DGenerator，无法生成 3D 模型");
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * 生成 3D 模型（带上游端点与凭据）。
     *
     * <p>不依赖 {@code ChatClient} 的直连渠道（如通用 HTTP 3D 服务）覆盖本方法，
     * 从 {@code endpoint} / {@code token} 取上游地址与凭据；默认忽略这两个参数，
     * 委托 {@link #generate(String, String, String, int, String)}。</p>
     *
     * @param prompt         提示词
     * @param refImageUrl    参考图 URL
     * @param format         目标格式
     * @param timeoutSeconds 超时秒数
     * @param model          模型标识
     * @param endpoint       上游端点（可空，实现可回落到自身配置）
     * @param token          上游凭据（可空）
     * @return 生成结果
     */
    default Model3DGenerationResult generate(String prompt, String refImageUrl, String format,
            int timeoutSeconds, String model, String endpoint, String token) {
        return generate(prompt, refImageUrl, format, timeoutSeconds, model);
    }

    /**
     * 图生 3D 渠道固定为 {@link ChannelKind#MODEL_3D}。
     *
     * @return {@link ChannelKind#MODEL_3D}
     */
    @Override
    default ChannelKind kind() {
        return ChannelKind.MODEL_3D;
    }

    /**
     * 图生 3D 渠道没有账号概念。
     *
     * @return 空集合
     */
    @Override
    default Set<AccountType> accountTypes() {
        return Set.of();
    }

    /**
     * 图生 3D 渠道无需绑定。
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
