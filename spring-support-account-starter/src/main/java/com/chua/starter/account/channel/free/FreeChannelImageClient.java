package com.chua.starter.account.channel.free;

import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageClientSetting;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.spi.ImageChannelProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 免费渠道聚合 {@link ImageClient}。
 *
 * <p><b>归属</b>：与 {@link FreeChannelChatClient} 一致，免费渠道是账号模块的聚合出口，
 * 出图模型属于账号模块内置的免登录文生图渠道（如 {@code image-pollinations} 的
 * {@code flux} / {@code turbo}），因此本实现只在账号模块维护。</p>
 *
 * <p>免费渠道的模型目录会把各免登录渠道的图片模型并入 {@code free-channel}
 * （见 AI 侧「从供应商获取模型」的聚合逻辑），若没有本实现，这些模型行在
 * {@code /v2/ai/generate/models} 里会被「供应商无图像实现」过滤掉，或选中后解析不到客户端。
 * 本类按模型标识反查内置文生图渠道并转交，让免费渠道下的图片模型真正可出图。</p>
 *
 * <p>出图是同步链路：{@link #createTask(String)} 显式抛
 * {@link UnsupportedOperationException}，由 AI 侧改走本地线程池同步执行
 * {@link #generate(String)}，与其它同步实现的约定一致。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi({"free-channel"})
public class FreeChannelImageClient implements ImageClient {

    /**
     * 免费渠道的 {@code ImageClient} SPI 扩展名。
     */
    public static final String EXTENSION = "free-channel";

    /**
     * 图片下载超时（秒）：免登录图床按需渲染，通常 10~40 秒，留足余量。
     */
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 180L;

    /**
     * 客户端配置（provider=free-channel）
     */
    private final ImageClientSetting setting;

    /**
     * 下载图片字节的 HTTP 客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 当前模型标识
     */
    private String model;

    /**
     * 目标宽度（{@code null} 表示按渠道默认）
     */
    private Integer width;

    /**
     * 目标高度（{@code null} 表示按渠道默认）
     */
    private Integer height;

    /**
     * 构造免费渠道图片生成客户端。
     *
     * @param setting 客户端配置
     */
    public FreeChannelImageClient(ImageClientSetting setting) {
        this.setting = setting;
        this.model = setting == null ? null : setting.getModel();
        this.width = setting == null ? null : setting.getWidth();
        this.height = setting == null ? null : setting.getHeight();
    }

    /**
     * 设置模型标识（flux / turbo 等）。
     *
     * @param model 模型标识
     * @return 当前实例
     */
    @Override
    public ImageClient model(String model) {
        this.model = model;
        return this;
    }

    /**
     * 设置出图尺寸。
     *
     * @param width  宽度
     * @param height 高度
     * @return 当前实例
     */
    @Override
    public ImageClient size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * 同步出图：按模型转交内置免登录文生图渠道，再把上游 URL 取回为图片对象。
     *
     * @param prompt 提示词
     * @return 生成的图片
     */
    @Override
    public BufferedImage generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        ImageChannelProvider channel = resolveChannel(model);
        ImageGenerationResult result = channel.generate(prompt, null, 1, orZero(width), orZero(height), model);
        return download(firstUrl(result));
    }

    /**
     * 免费出图渠道没有上游任务号：显式不支持，由调用方改走同步路径。
     *
     * @param prompt 提示词
     * @return 不会返回
     */
    @Override
    public String createTask(String prompt) {
        throw new UnsupportedOperationException("免费出图渠道为同步实现，不提供上游任务号");
    }

    /**
     * 免费出图渠道没有上游任务可查。
     *
     * @param taskId 上游任务号
     * @return 不会返回
     */
    @Override
    public ImageResponse queryTask(String taskId) {
        throw new UnsupportedOperationException("免费出图渠道为同步实现，无需轮询");
    }

    /**
     * 空尺寸按 0 处理（渠道内部会回落到 ratio 默认值）。
     *
     * @param value 尺寸
     * @return 非空值或 0
     */
    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 按模型标识反查内置文生图渠道。
     *
     * <p>模型标识来自各渠道自己的 {@link ImageChannelProvider#listModels()}，
     * 因此新增免登录出图渠道时无需改动本类。多个渠道声明同名模型时取优先级最高者；
     * 未指定模型时取声明了模型且优先级最高的渠道。</p>
     *
     * @param model 模型标识，可为空
     * @return 命中的渠道，未命中抛异常
     */
    private static ImageChannelProvider resolveChannel(String model) {
        boolean byModel = model != null && !model.isBlank();
        String target = byModel ? model.trim() : null;
        ImageChannelProvider matched = null;
        for (ImageChannelProvider channel : ChannelRegistry.imageChannels().values()) {
            if (channel == null || !declares(channel)) {
                continue;
            }
            if (byModel && !supports(channel, target)) {
                continue;
            }
            if (matched == null || channel.priority() > matched.priority()) {
                matched = channel;
            }
        }
        if (matched == null) {
            throw new IllegalStateException(byModel
                    ? "免费渠道下没有可用的出图渠道声明模型: " + target
                    : "免费渠道下没有可用的出图渠道");
        }
        return matched;
    }

    /**
     * 渠道是否声明了可用模型。
     *
     * @param channel 文生图渠道
     * @return true 表示至少有一个模型
     */
    private static boolean declares(ImageChannelProvider channel) {
        List<Map<String, Object>> models = channel.listModels();
        return models != null && !models.isEmpty();
    }

    /**
     * 渠道是否声明了指定模型。
     *
     * @param channel 文生图渠道
     * @param model   模型标识
     * @return true 表示命中
     */
    private static boolean supports(ImageChannelProvider channel, String model) {
        List<Map<String, Object>> models = channel.listModels();
        if (models == null) {
            return false;
        }
        for (Map<String, Object> row : models) {
            Object id = row == null ? null : row.get("id");
            if (id != null && model.equalsIgnoreCase(String.valueOf(id).trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取第一张图片的访问地址（优先原图，其次预览图）。
     *
     * @param result 渠道生成结果
     * @return 图片地址
     */
    private static String firstUrl(ImageGenerationResult result) {
        if (result == null || result.images() == null || result.images().isEmpty()) {
            throw new IllegalStateException("免费出图渠道未返回图片");
        }
        ImageGenerationResult.GeneratedImage image = result.images().get(0);
        String url = image == null ? null : image.oriUrl();
        if (url == null || url.isBlank()) {
            url = image == null ? null : image.rawUrl();
        }
        if (url == null || url.isBlank()) {
            url = image == null ? null : image.thumbUrl();
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("免费出图渠道返回的图片缺少地址");
        }
        return url;
    }

    /**
     * 下载图片并解码为图片对象。
     *
     * @param url 图片地址
     * @return 图片
     */
    private BufferedImage download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(DOWNLOAD_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("免费出图渠道返回 HTTP " + response.statusCode());
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                throw new IllegalStateException("免费出图渠道返回的内容不是可识别的图片");
            }
            return image;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("下载免费出图结果被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("下载免费出图结果失败: " + e.getMessage(), e);
        }
    }
}
