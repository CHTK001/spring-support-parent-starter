package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.VideoChannelProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vidu 文生视频渠道。
 *
 * <p>底层复用 {@code vidu-proxy} 的 {@code ChatClient} 扩展：浏览器会话以
 * Cookie 登录态调用 vidu.cn 网页端的任务接口（创建 → 轮询 → 出片），
 * {@code appKey} 即 Vidu 站点 Cookie。渠道本身不持久化任何凭据，
 * 需要登录态时由请求体临时携带 Cookie，调用结束即释放。</p>
 *
 * <p>未引入 {@code utils-support-playwright-starter} 时，渠道仍会出现在列表中，
 * 但调用会给出「未注册的 ChatClient 扩展」的明确错误。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi("video-vidu")
public class ViduVideoChannel implements VideoChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "video-vidu";

    /**
     * 底层 ChatClient 扩展名
     */
    private static final String EXTENSION = "vidu-proxy";

    /**
     * 渠道优先级：Vidu 需要登录态 Cookie，作为试点渠道排在豆包 / 通义之后
     */
    private static final int PRIORITY = 70;

    /**
     * 渠道标识。
     *
     * @return {@code video-vidu}
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
        return "Vidu文生视频";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code vidu-proxy}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：Vidu 需要登录态 Cookie，作为试点渠道排在豆包 / 通义之后。
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
     * <p>Vidu 网页端当前默认模型版本为 3.2；底层 proxy 的 {@code generateVideo}
     * 将模型名映射为请求体中的 {@code model_version}。这里声明模型是为了把它
     * 物化进模型管理、进入统一生成目录并接受启停管控。</p>
     *
     * @return 模型列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        Map<String, Object> model = new LinkedHashMap<>(2);
        model.put("id", "vidu-video");
        model.put("name", "Vidu视频生成");
        return List.of(model);
    }
}
