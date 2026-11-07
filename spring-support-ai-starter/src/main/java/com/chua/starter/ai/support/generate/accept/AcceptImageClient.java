package com.chua.starter.ai.support.generate.accept;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageClientSetting;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 验收用图片实现（仅本机验收包，不随业务发布）。
 *
 * <p>本机没有可用的出图供应商，场景图会一直 FAILED，图生视频那条路就永远
 * 走不到「拿场景图当首帧」。这里提供一个最小可用的 {@link ImageClient}：
 * 生成时直接去 mock（默认 {@code http://127.0.0.1:18181}）取一张固定图，
 * 让场景图能正常落成 SUCCESS，后面的视频任务才有参考图可传。</p>
 *
 * @author CH
 */
@SpiDefault
@Spi("acceptimage")
public class AcceptImageClient implements ImageClient {

    /**
     * 未配置 baseUrl 时的兜底地址（本机 mock）
     */
    private static final String DEFAULT_BASE = "http://127.0.0.1:18181";

    /**
     * 客户端设置（含 baseUrl）
     */
    private final ImageClientSetting setting;

    /**
     * 模型名
     */
    private String model = "";

    /**
     * 宽
     */
    private int width;

    /**
     * 高
     */
    private int height;

    /**
     * @param setting 客户端设置（含 baseUrl）
     */
    public AcceptImageClient(ImageClientSetting setting) {
        this.setting = setting;
    }

    @Override
    public ImageClient model(String model) {
        this.model = model;
        return this;
    }

    @Override
    public ImageClient size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public ImageClient prompt(String prompt) {
        return this;
    }

    @Override
    public BufferedImage generate(String prompt) {
        byte[] bytes = fetch();
        if (bytes == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String createTask(String prompt) {
        // 抛「不支持异步」让生成层改走本地同步分支（runImageAsync）；
        // 否则任务会被记成 PENDING 等轮询，而世界沙盒的素材阶段只看终态。
        throw new UnsupportedOperationException("accept image is a synchronous implementation");
    }

    @Override
    public ImageResponse queryTask(String taskId) {
        byte[] bytes = fetch();
        return ImageResponse.builder()
                .taskId(taskId)
                .status(bytes == null ? ImageResponse.Status.FAILED : ImageResponse.Status.SUCCESS)
                .imageBytes(bytes)
                .errorMessage(bytes == null ? "accept image mock unreachable" : null)
                .build();
    }

    @Override
    public List<ModelDefinition> models() {
        return List.of();
    }

    /**
     * mock 地址（去掉末尾斜杠）
     *
     * @return baseUrl
     */
    private String base() {
        String b = setting == null ? null : setting.getBaseUrl();
        if (b == null || b.isBlank()) {
            return DEFAULT_BASE;
        }
        b = b.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    /**
     * 取一张固定的验收图
     *
     * @return PNG 字节；mock 不可达时为 null
     */
    private byte[] fetch() {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(base() + "/accept/image.png")
                    .toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() / 100 != 2) {
                return null;
            }
            try (var in = c.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
