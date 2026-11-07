package com.chua.starter.ai.support.generate.accept;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoClientSetting;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 验收用视频实现（仅本机验收包，不随业务发布）。
 *
 * <p>本机没有任何登记过的视频供应商，世界沙盒的「图生视频 / 文生视频」链路
 * 会一直走降级分支，肉眼看不到视频。这里提供一个最小可用的 {@link VideoClient}：
 * 建任务时把收到的参数（模型、尺寸、时长、提示词、是否带了参考图）原样发给
 * 本地 mock（默认 {@code http://127.0.0.1:18181}），由 mock 记录下来供断言；
 * 轮询一律立刻返回成功，并给出 mock 上的一个地址。</p>
 *
 * <p>关键可验证点：{@code hasReference} 为 true 说明场景图确实被当成首帧传给了
 * 视频模型（图生视频），为 false 则是文生视频。</p>
 *
 * @author CH
 */
@SpiDefault
@Spi("acceptvideo")
public class AcceptVideoClient implements VideoClient {

    /**
     * 未配置 baseUrl 时的兜底地址（本机 mock）
     */
    private static final String DEFAULT_BASE = "http://127.0.0.1:18181";

    private String model = "";

    private int width = 1280;

    private int height = 720;

    private Integer duration;

    private byte[] reference;

    private String referenceUrl;

    private final VideoClientSetting setting;

    /**
     * @param setting 客户端设置（含 baseUrl）
     */
    public AcceptVideoClient(VideoClientSetting setting) {
        this.setting = setting;
    }

    @Override
    public VideoClient model(String model) {
        this.model = model;
        return this;
    }

    @Override
    public VideoClient size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public VideoClient duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    @Override
    public VideoClient prompt(String prompt) {
        return this;
    }

    @Override
    public VideoClient referenceImage(byte[] image) {
        this.reference = image;
        return this;
    }

    @Override
    public boolean supportsReferenceImageUrl() {
        // 收字节而不是公网地址：本机没有可用的文件存储服务，
        // 走 URL 分支会在发布参考图这一步就失败，图生视频永远验不到。
        // 真实客户端里 ZhipuVideoClient 也是收 base64 字节的这一类。
        return false;
    }

    @Override
    public VideoClient referenceImageUrl(String url) {
        this.referenceUrl = url;
        return this;
    }

    @Override
    public String createTask(String prompt) {
        String body = "{\"model\":" + quote(model)
                + ",\"prompt\":" + quote(prompt)
                + ",\"size\":\"" + width + "x" + height + "\""
                + ",\"duration\":" + (duration == null ? "null" : duration)
                + ",\"hasReference\":" + (reference != null && reference.length > 0)
                + ",\"referenceBytes\":" + (reference == null ? 0 : reference.length)
                + ",\"referenceUrl\":" + quote(referenceUrl == null ? "" : referenceUrl)
                + "}";
        String resp = post("/accept/video/task", body);
        String id = resp == null || resp.isBlank() ? "accept-task-" + System.currentTimeMillis() : resp.trim();
        // mock 可能按 JSON 字符串回任务号：带引号的任务号会被拼进播放地址，浏览器取到的 URL 就坏了
        if (id.length() > 1 && id.startsWith("\"") && id.endsWith("\"")) {
            id = id.substring(1, id.length() - 1);
        }
        return id;
    }

    @Override
    public VideoResponse queryTask(String taskId) {
        VideoResponse r = VideoResponse.builder()
                .taskId(taskId)
                .status(VideoResponse.Status.SUCCESS)
                .videoUrl(base() + "/accept/video/" + taskId + ".mp4")
                .progress(100)
                .build();
        return r;
    }

    @Override
    public List<ModelDefinition> models() {
        return List.of();
    }

    private String base() {
        String b = setting == null ? null : setting.getBaseUrl();
        if (b == null || b.isBlank()) {
            return DEFAULT_BASE;
        }
        b = b.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    private static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private String post(String path, String body) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(base() + path).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) {
                return null;
            }
            try (var in = c.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
