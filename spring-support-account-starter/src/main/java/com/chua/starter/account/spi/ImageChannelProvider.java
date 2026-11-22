package com.chua.starter.account.spi;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.channel.KeylessMediaChannel;

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
 *       {@code META-INF/extensions/com.chua.starter.account.spi.ImageChannelProvider} 登记。</li>
 * </ol>
 *
 * <p>渠道种类固定为 {@link ChannelKind#IMAGE}，账号类型为空集合。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ImageChannelProvider extends ChannelProvider, KeylessMediaChannel {

    /**
     * 单次生成允许的最大张数。
     *
     * <p>文生图按张计费、耗时随张数线性增长，API 边界与 SPI 入口共用同一个上限，
     * 避免「控制器收了 4 张、SPI 直调却能要 50 张」的口径分叉。</p>
     */
    int MAX_IMAGES_PER_CALL = 4;

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
     * 本渠道产出的任务类型：文生图渠道固定为 {@code image_generation}。
     *
     * <p>免费渠道聚合目录按此字段給模型行打能力标签，前端才能把出图模型与对话模型分开。</p>
     *
     * @return 任务类型列表
     */
    @Override
    default List<String> tasks() {
        return List.of("image_generation");
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
        // 先校验入参，再解析底层客户端：出图是按张计费的同步调用，参数不合法时必须立刻拒绝，
        // 不能因为宿主恰好缺某个 ChatClient 扩展就把它伪装成「渠道不可用」（IllegalStateException）。
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        if (n > MAX_IMAGES_PER_CALL) {
            throw new IllegalArgumentException("文生图按张计费，单次最多 " + MAX_IMAGES_PER_CALL
                    + " 张，当前请求 n=" + n);
        }
        ChatClient client = chatClient();
        try {
            // ChatClient#generateImage 没有 model 形参，模型必须通过 client.model(...) 注入；
            // 此前直接传 null 会导致前端选的模型完全不生效（请求成功但永远走默认模型）
            if (model != null && !model.isBlank()) {
                client.model(model.trim());
            }
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
     * 本渠道是否支持参考图（图生图）。
     *
     * <p>默认不支持；具备图生图能力的渠道覆盖本方法返回 {@code true}。</p>
     *
     * @return true 表示渠道具备图生图能力
     */
    default boolean supportsReferenceImage() {
        return false;
    }

    /**
     * 本渠道对指定模型是否支持参考图（图生图）。
     *
     * <p>渠道整体具备图生图能力时，具体模型也可能不支持（如 Pollinations 的
     * flux / turbo 只能文生图，kontext 等编辑模型才支持）。默认沿用渠道级结论；
     * 有模型级差异的渠道覆盖本方法。</p>
     *
     * @param model 模型标识，可为空
     * @return true 表示该模型支持参考图
     */
    default boolean supportsReferenceImage(String model) {
        return supportsReferenceImage();
    }

    /**
     * 渠道为指定模型声明的生成能力节点（并入免登录模型目录的 capabilities）。
     *
     * <p>例如对支持参考图的模型声明 {@code generate.referenceImage=true}，
     * 前端据此展示参考图入口。返回 {@code null} 表示无额外能力声明。</p>
     *
     * @param model 模型标识
     * @return generate 能力节点，可为 {@code null}
     */
    default Map<String, Object> generateCapability(String model) {
        return null;
    }

    /**
     * 生成图片（参考图感知重载）。
     *
     * <p>带参考图时走图生图（img2img）；参考图为空时等价于
     * {@link #generate(String, String, int, int, int, String)}。
     * 渠道未覆盖本方法时视为不支持参考图，直接抛
     * {@link UnsupportedOperationException}，由上层在任务落库前给出明确错误，
     * 绝不允许静默忽略参考图按文生图出图。</p>
     *
     * @param prompt         提示词
     * @param ratio          宽高比（如 {@code 1:1}），可为空
     * @param n              张数，至少 1
     * @param width          宽度（0 表示按 ratio）
     * @param height         高度（0 表示按 ratio）
     * @param model          出图模型；为空时用渠道默认模型
     * @param referenceImage 参考图字节，可为空
     * @param apiKey         渠道 API Key（来自渠道行凭据），可为空
     * @return 生成结果
     */
    default ImageGenerationResult generate(String prompt, String ratio, int n, int width, int height, String model,
            byte[] referenceImage, String apiKey) {
        if (referenceImage == null || referenceImage.length == 0) {
            return generate(prompt, ratio, n, width, height, model);
        }
        throw new UnsupportedOperationException("渠道 " + channel() + " 不支持参考图（图生图）");
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
