package com.chua.starter.channel.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ImageChannelProvider;

/**
 * Trae 文生图渠道 —— 复用 {@code trae} 的 {@code ChatClient#generateImage}。
 *
 * <p>协议事实源自 {@code dsh-connect-trae} 的 {@code image-gen-trae.ts}：
 * {@code /api/ide/v1/tool_text_to_image}（同步出图，返回 TOS uri）→
 * {@code /api/ide/v1/get_resource_url}（换签名下载地址）。尺寸需满足 Ark 的总像素区间，
 * 否则上游直接拒绝（512x512 / 1024x1024 均不可用，最小合法为 1920x1920）。</p>
 *
 * <p>需要本机已登录 Trae IDE（或渠道页填写 JWT）；未就绪时端点返回明确原因，
 * 渠道卡片仍正常展示。生图按次计费（约 8.8 积分/张），请谨慎在演示环境点击。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("image-trae")
public class TraeImageChannel implements ImageChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "image-trae";

    /**
     * 底层 ChatClient SPI 扩展名
     */
    public static final String EXTENSION = "trae";

    /**
     * 渠道标识。
     *
     * @return {@code image-trae}
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
        return "Trae 文生图";
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
     * 优先级：画质更高但有积分成本，排在免费渠道之后。
     *
     * @return {@code 90}
     */
    @Override
    public int priority() {
        return 90;
    }
}
