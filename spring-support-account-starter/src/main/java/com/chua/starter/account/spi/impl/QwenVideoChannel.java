package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.VideoChannelProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通义千问文生视频渠道。
 *
 * <p>底层复用 {@code qwen-proxy} 的 {@code ChatClient} 扩展：浏览器会话驱动通义的
 * 视频生成面板，{@code appKey} 即通义站点 Cookie。渠道本身不持久化任何凭据，
 * 需要登录态时由请求体临时携带 Cookie，调用结束即释放。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi("video-qwen")
public class QwenVideoChannel implements VideoChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "video-qwen";

    /**
     * 底层 ChatClient 扩展名
     */
    private static final String EXTENSION = "qwen-proxy";

    /**
     * 渠道优先级：作为豆包的备用渠道
     */
    private static final int PRIORITY = 50;

    /**
     * 渠道标识。
     *
     * @return {@code video-qwen}
     */
    @Override
    public String channel() {
        return CHANNEL;
    }

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    @Override
    public String displayName() {
        return "通义千问文生视频";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code qwen-proxy}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：作为豆包的备用渠道。
     *
     * @return 渠道优先级
     */
    @Override
    public int priority() {
        return PRIORITY;
    }

    /**
     * 渠道支持的视频模型。
     *
     * <p>通义视频生成面板当前只有一个内置模型；底层 proxy 的 {@code generateVideo}
     * 不消费模型参数（面板固定行为），这里声明模型是为了把它物化进模型管理、
     * 进入统一生成目录并接受启停管控。</p>
     *
     * @return 模型列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        Map<String, Object> model = new LinkedHashMap<>(2);
        model.put("id", "qwen-video");
        model.put("name", "通义视频生成");
        return List.of(model);
    }
}
