package com.chua.starter.channel.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ImageChannelProvider;

/**
 * DuckDuckGo 文生图渠道 —— 复用 {@code duckduckgo} 的 {@code ChatClient#generateImage}。
 *
 * <p>底层实现走 {@code /duckchat/v1/chat} 的 SSE 流，请求体带
 * {@code canDelegateImageGeneration=true} 与 {@code toolChoice.GenerateImage=true}，
 * 由客户端解析 {@code generated-image} 事件；完全免登录，也不需要任何密钥。</p>
 *
 * <p>上游对出图频率与张数有限制，超限会返回 {@code image_generation_limit}，
 * 端点会把该错误原样透出给前端。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("image-duckduckgo")
public class DuckDuckGoImageChannel implements ImageChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "image-duckduckgo";

    /**
     * 底层 ChatClient SPI 扩展名（DuckChatClient 的 {@code @Spi} 别名）
     */
    public static final String EXTENSION = "duckduckgo";

    /**
     * 渠道标识。
     *
     * @return {@code image-duckduckgo}
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
        return "DuckDuckGo 文生图";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code duckduckgo}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：完全免登录、无需任何配置，排在最前（高于 Pollinations 的 URL 直链）。
     *
     * @return {@code 110}
     */
    @Override
    public int priority() {
        return 110;
    }
}
