package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.openai.support.OpenAiChatClient;

/**
 * Magpie 网关的对话客户端。
 *
 * <p>Magpie 的数据面（{@code /v1/chat/completions}、{@code /v1/responses}、
 * {@code /v1/messages}）是 OpenAI 兼容协议，故本类只做「SPI 别名注册」这一件事：
 * 让供应商行 {@code platform=magpie} 能直接用 {@code base_url=<网关>/v1} 建客户端，
 * 而不必退回 {@code openai} 或 {@code custom} 别名。</p>
 *
 * <p>路由场景（{@code FreeChannelChatClient} 按模型名选目标）<b>不经过本类</b>：
 * 那里由 {@code MagpieRouteSource} 生成 {@code provider=openai} 的
 * {@link com.chua.common.support.ai.chat.route.RouteTarget}。本类只服务
 * 「在供应商管理里直接加一条 magpie 供应商」的用法。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi("magpie")
public class MagpieChatClient extends OpenAiChatClient {

    /**
     * 构造。
     *
     * @param setting 客户端配置（{@code baseUrl} 指向 {@code <网关>/v1}）
     */
    public MagpieChatClient(ChatClientSetting setting) {
        super(setting);
    }
}
