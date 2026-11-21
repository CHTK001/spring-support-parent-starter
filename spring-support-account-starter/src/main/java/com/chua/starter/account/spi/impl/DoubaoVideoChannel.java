package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.VideoChannelProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆包文生视频渠道。
 *
 * <p>底层复用 {@code doubao-proxy} 的 {@code ChatClient} 扩展：浏览器会话驱动豆包的
 * 视频生成面板，{@code appKey} 即豆包站点 Cookie。渠道本身不持久化任何凭据，
 * 需要登录态时由请求体临时携带 Cookie，调用结束即释放。</p>
 *
 * <p>未引入 {@code utils-support-playwright-starter}（或 jvppeteer 版本）时，
 * 渠道仍会出现在列表中，但调用会给出「未注册的 ChatClient 扩展」的明确错误。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi("video-doubao")
public class DoubaoVideoChannel implements VideoChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "video-doubao";

    /**
     * 底层 ChatClient 扩展名
     */
    private static final String EXTENSION = "doubao-proxy";

    /**
     * 渠道优先级：豆包视频生成面板默认可用，作为优先渠道
     */
    private static final int PRIORITY = 60;

    /**
     * 渠道标识。
     *
     * @return {@code video-doubao}
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
        return "豆包文生视频";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code doubao-proxy}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：豆包视频生成面板默认可用，作为优先渠道。
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
     * <p>豆包视频生成面板当前只有一个内置模型；底层 proxy 的 {@code generateVideo}
     * 不消费模型参数（面板固定行为），这里声明模型是为了把它物化进模型管理、
     * 进入统一生成目录并接受启停管控。</p>
     *
     * @return 模型列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        Map<String, Object> model = new LinkedHashMap<>(2);
        model.put("id", "doubao-video");
        model.put("name", "豆包视频生成");
        return List.of(model);
    }
}
