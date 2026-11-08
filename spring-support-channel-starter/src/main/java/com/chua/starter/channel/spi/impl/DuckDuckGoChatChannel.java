// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.channel.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ChatChannelProvider;

/**
 * DuckDuckGo 对话渠道 —— 把 {@code duck.ai} 的免登录对话包装进渠道管理。
 *
 * <p>底层是 {@code com.chua.duckduckgo.support.DuckChatClient}（{@code ChatClient} SPI 别名
 * {@code duck} / {@code duckduckgo}）：不需要 API Key，也不需要绑定账号，因此本渠道
 * 没有连接行与凭据，渠道管理页只展示内置模型并可直接「试对话」。</p>
 *
 * <p>本类不直接依赖 duckduckgo 模块，运行时通过 {@code ChatClient} SPI 按
 * {@code duckduckgo} 扩展名解析；未引入该模块时，模型列表为空、试对话返回明确的
 * 「未注册的 ChatClient 扩展」提示，渠道卡片本身仍可见。</p>
 *
 * <p>协议细节（vqd 挑战脚本必须在真实浏览器执行）由 DuckChatClient 负责，
 * 本适配器只做渠道描述与调用编排，每次调用新建客户端、结束即 {@code close()} 释放浏览器进程。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("chat-duckduckgo")
public class DuckDuckGoChatChannel implements ChatChannelProvider {

    /**
     * 渠道标识（前端路由 / 持久化用）
     */
    public static final String CHANNEL = "chat-duckduckgo";

    /**
     * 底层 ChatClient SPI 扩展名（DuckChatClient 的 {@code @Spi} 别名）
     */
    private static final String EXTENSION = "duckduckgo";

    /**
     * 渠道标识。
     *
     * @return {@code chat-duckduckgo}
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
        return "DuckDuckGo 对话";
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
     * 优先级：免登录渠道里默认置顶，便于渠道管理页优先呈现。
     *
     * @return {@code 50}
     */
    @Override
    public int priority() {
        return 50;
    }

    /**
     * DuckDuckGo 客户端在同一条 SSE 流上支持 {@code GenerateImage} 委派，故也声明可出图。
     *
     * @return {@code true}
     */
    @Override
    public boolean supportsImage() {
        return true;
    }
}