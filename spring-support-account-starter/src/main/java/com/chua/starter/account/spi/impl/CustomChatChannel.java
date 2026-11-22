// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChatChannelProvider;
import com.chua.starter.common.support.channel.KeylessChatChannel;

import java.util.List;
import java.util.Map;

/**
 * 自定义提供商对话渠道 —— 把「OpenAI 兼容协议」作为一种渠道登记进渠道管理。
 *
 * <p>底层是 {@code com.chua.openai.support.OpenAiChatClient}（{@code ChatClient} SPI 别名含
 * {@code custom}）：与 {@code kilo} / {@code pollinations} 等绑定单一网关的实现不同，
 * {@code custom} 不锁定上游——缺省落 OpenAI 官方地址（{@code https://api.openai.com/v1}），
 * baseUrl 可经 {@code ChatClientSetting} 覆盖到任意 OpenAI 兼容网关。</p>
 *
 * <p>凭据口径：API Key 不落库，试对话时经请求体 {@code token} 临时传入本次调用
 * （标准 {@code Authorization: Bearer} 头），与免登录渠道「不产生连接行、不落凭据」的定位一致。</p>
 *
 * <p>本类不直接依赖 openai 模块，运行时通过 {@code ChatClient} SPI 按 {@code custom}
 * 扩展名解析；未引入该模块时，模型列表为空、试对话返回明确的「未注册的 ChatClient 扩展」
 * 提示，渠道卡片本身仍可见。</p>
 *
 * <p>自定义提供商没有内置模型目录（模型由使用方自备，试对话时需显式指定 {@code model}），
 * 因此本渠道覆盖 {@link #listModels()} 返回空清单：默认实现会新建客户端并请求上游
 * {@code /models}，对「使用方自备 key / baseUrl」的渠道毫无意义，只会让渠道页反复报
 * modelsError。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Spi("chat-custom")
public class CustomChatChannel implements ChatChannelProvider, KeylessChatChannel {

    /**
     * 渠道标识（前端路由 / 持久化用）
     */
    public static final String CHANNEL = "chat-custom";

    /**
     * 底层 ChatClient SPI 扩展名（OpenAiChatClient 的 {@code @Spi} 别名）
     */
    private static final String EXTENSION = "custom";

    /**
     * 渠道标识。
     *
     * @return {@code chat-custom}
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
        return "自定义提供商";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code custom}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：免登录渠道里排在 DuckDuckGo（50）、Pollinations（60）之后。
     *
     * @return {@code 70}
     */
    @Override
    public int priority() {
        return 70;
    }

    /**
     * 渠道内置模型清单：自定义提供商不内置模型目录，返回空清单。
     *
     * <p>试对话时由使用方显式指定模型名（如 {@code gpt-4o-mini}）；这里刻意不走
     * {@link ChatChannelProvider#listModels()} 的默认实现——它会新建客户端并请求上游
     * {@code /models}，对「使用方自备 key / baseUrl」的渠道没有意义，还会在渠道页
     * 制造无意义的失败记录。</p>
     *
     * @return 空列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        return List.of();
    }
}
