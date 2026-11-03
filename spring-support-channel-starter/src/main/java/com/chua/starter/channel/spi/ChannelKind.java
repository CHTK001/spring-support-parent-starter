package com.chua.starter.channel.spi;

/**
 * 渠道种类。
 *
 * <p>描述一个渠道「属于哪一类」，与{@link AccountType 账号类型}是两个正交维度：
 * 渠道种类回答「这个渠道提供什么能力」，账号类型回答「这个渠道上的账号是长期的还是一次性的」。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public enum ChannelKind {

    /**
     * AI 推理渠道：通过绑定 / 授权拿到长期凭据，产出 LLM 路由目标。
     */
    AI,

    /**
     * ChatClient 免登录对话渠道：直接复用 {@code ChatClient} SPI 实现（如 DuckDuckGo），
     * 无绑定流程、无持久化凭据，按需新建客户端即可对话。
     */
    CHAT,

    /**
     * 短信接码渠道：领取临时虚拟号码并读取其收到的短信。
     */
    SMS,

    /**
     * 邮件收信渠道：领取临时邮箱并读取其收到的邮件。
     */
    EMAIL
}
