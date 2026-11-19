// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ChatChannelProvider;
import com.chua.starter.common.support.channel.KeylessChatChannel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pollinations 对话渠道 —— 把 Pollinations 文本网关的免登录对话包装进渠道管理。
 *
 * <p>协议（2026-11-19 实测）：</p>
 * <ul>
 *   <li>匿名层免登录、免 API Key：{@code GET https://text.pollinations.ai/models} 的
 *       {@code openai-fast} 条目为 {@code tier=anonymous, tools=true}；</li>
 *   <li>OpenAI 兼容入口 {@code POST https://text.pollinations.ai/openai/chat/completions}
 *       实测 200。底层复用 utils 的 {@code OpenAiChatClient}（{@code @Spi} 别名
 *       {@code pollinations}），缺省 baseUrl 由它兜底到
 *       {@code https://text.pollinations.ai/openai}。</li>
 * </ul>
 *
 * <p>本类不直接依赖 openai 模块，运行时通过 {@code ChatClient} SPI 按 {@code pollinations}
 * 扩展名解析；未引入该模块时模型列表为空、试对话返回「未注册的 ChatClient 扩展」提示，
 * 渠道卡片本身仍可见。与 DuckDuckGo 渠道一样没有连接行与凭据，渠道管理页展示内置模型后
 * 可直接「试对话」。</p>
 *
 * <p>匿名层有限速，部分模型要求 {@code referrer} 头；首批只暴露匿名层可用的
 * {@code openai-fast}（GPT-OSS 20B，官方上下文 128k）。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Spi("chat-pollinations")
public class PollinationsChatChannel implements ChatChannelProvider, KeylessChatChannel {

    /**
     * 渠道标识（前端路由 / 持久化用）
     */
    public static final String CHANNEL = "chat-pollinations";

    /**
     * 底层 ChatClient SPI 扩展名（OpenAiChatClient 的 {@code @Spi} 别名）
     */
    private static final String EXTENSION = "pollinations";

    /**
     * 渠道标识。
     *
     * @return {@code chat-pollinations}
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
        return "Pollinations 对话";
    }

    /**
     * 底层 ChatClient 扩展名。
     *
     * @return {@code pollinations}
     */
    @Override
    public String extension() {
        return EXTENSION;
    }

    /**
     * 优先级：免登录渠道里排在 DuckDuckGo（50）之后。
     *
     * @return {@code 60}
     */
    @Override
    public int priority() {
        return 60;
    }

    /**
     * 渠道内置模型清单（免费渠道的模型目录也读这一份）。
     *
     * <p>静态声明、不触达上游：Pollinations 匿名层模型目录虽可经
     * {@code GET /models} 在线拉取，但其中仅匿名层（{@code tier=anonymous}）模型
     * 对免登录渠道可用，且目录随上游波动；首批只暴露实测可用的 {@code openai-fast}。</p>
     *
     * @return 模型列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        Map<String, Object> model = new LinkedHashMap<>(3);
        model.put("id", "openai-fast");
        model.put("name", "GPT-OSS 20B (Pollinations)");
        model.put("contextWindow", 131072);
        return List.of(model);
    }
}
