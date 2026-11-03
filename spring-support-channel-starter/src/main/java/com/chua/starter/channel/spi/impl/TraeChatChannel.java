package com.chua.starter.channel.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ChatChannelProvider;

/**
 * Trae 对话渠道 —— 把 Trae IDE 的本地登录态包装进渠道管理。
 *
 * <p>底层是 {@code com.chua.trae.support.spi.TraeChatClient}（{@code ChatClient} SPI 别名
 * {@code trae} / {@code trae-cn} / {@code trae-global}）：协议与鉴权事实源自
 * {@code dsh-connect-trae}（{@code llm_utils_chat} SSE + {@code storage.json} 登录态），
 * 因此本渠道同样没有绑定流程与持久化凭据。</p>
 *
 * <p>登录态来源按优先级：渠道页填写的 JWT token → 环境变量 {@code TRAE_DATA_DIR} →
 * 本机 Windows 的 {@code %APPDATA%\Trae CN} / {@code %APPDATA%\Trae}。
 * 未就绪时模型清单仍可读（静态模型表），试对话返回明确的「未检测到 Trae 登录态」提示。</p>
 *
 * <p>该渠道同时具备文生图能力（{@link #supportsImage()}），渠道卡片上会打「可出图」标。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("chat-trae")
public class TraeChatChannel implements ChatChannelProvider {

    /**
     * 渠道标识（前端路由 / 持久化用）
     */
    public static final String CHANNEL = "chat-trae";

    /**
     * 底层 ChatClient SPI 扩展名（TraeChatClient 的 {@code @Spi} 别名）
     */
    public static final String EXTENSION = "trae";

    /**
     * 渠道标识。
     *
     * @return {@code chat-trae}
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
        return "Trae 对话";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code trae}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：低于 DuckDuckGo（后者完全零配置），因为 Trae 需要本机已有登录态或手填 JWT。
     *
     * @return {@code 40}
     */
    @Override
    public int priority() {
        return 40;
    }

    /**
     * Trae 客户端实现了 {@code generateImage}，渠道同时支持文生图。
     *
     * @return {@code true}
     */
    @Override
    public boolean supportsImage() {
        return true;
    }
}
