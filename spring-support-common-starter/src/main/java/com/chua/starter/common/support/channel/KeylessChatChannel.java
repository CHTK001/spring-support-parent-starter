package com.chua.starter.common.support.channel;

import java.util.List;
import java.util.Map;

/**
 * 免登录对话渠道的中立契约。
 *
 * <p>渠道的完整实现住在账号模块（{@code ChatChannelProvider}，带渠道种类 / 账号类型等
 * 账号语义），但<b>免费渠道（free-channel）的模型路由</b>住在 AI 模块，而 AI 模块按设计
 * 不反向依赖账号模块（{@code spring-support-account-starter} 由宿主显式引入）。</p>
 *
 * <p>因此在 common-starter 放一份最小契约：账号模块的渠道实现同时实现本接口，
 * AI 模块通过 {@code ServiceProvider} 发现它们，把「渠道内置模型」并进免费渠道路由表。
 * 只暴露路由所需的四项事实：渠道标识、底层 ChatClient 扩展名、展示名、内置模型清单。</p>
 *
 * <p>没有上游地址：这类渠道的 {@code ChatClient} 自己持有协议与会话，{@code baseUrl}
 * 恒为空，路由只负责「模型名 → 哪个聊天客户端」。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface KeylessChatChannel {

    /**
     * 渠道标识（如 {@code chat-duckduckgo}）。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 底层 {@code ChatClient} SPI 扩展名（如 {@code duckduckgo}）。
     *
     * @return 扩展名
     */
    String extension();

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    default String displayName() {
        return channel();
    }

    /**
     * 同 kind 多渠道并存时的默认排序依据（越小越靠前）。
     *
     * @return 优先级
     */
    default int priority() {
        return 0;
    }

    /**
     * 渠道内置模型清单（供渠道管理页与免费渠道模型目录使用）。
     *
     * @return 模型列表，每项含 {@code id} / {@code name} / 可选 {@code contextWindow}
     */
    List<Map<String, Object>> listModels();
}
