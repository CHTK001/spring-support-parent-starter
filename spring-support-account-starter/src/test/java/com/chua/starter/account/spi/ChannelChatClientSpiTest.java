// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.spi;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.spi.impl.PollinationsImageChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 渠道 SPI 注册与 ChatClient 扩展解析的回归测试（纯内存，不触达网络）。
 *
 * <p>覆盖三件事：</p>
 * <ol>
 *   <li>Trae / DuckDuckGo 的 {@code ChatClient} 扩展能被 {@link ServiceProvider} 解析到
 *       —— 即 {@code META-INF/extensions/com.chua.common.support.ai.chat.ChatClient} 已生效；</li>
 *   <li>对话渠道 {@code chat-trae} 注册进 {@link ChannelRegistry#chatChannels()}；</li>
 *   <li>文生图渠道 {@code image-pollinations} / {@code image-trae} 注册进
 *       {@link ChannelRegistry#imageChannels()}，且免登录渠道的 URL 拼装符合上游约定。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
class ChannelChatClientSpiTest {

    /**
     * 验证 Trae 的 ChatClient 扩展可解析，且未登录时模型清单仍可读。
     */
    @Test
    void traeChatClientExtensionIsResolvable() {
        ChatClientSetting setting = ChatClientSetting.builder().provider("trae").build();
        ChatClient client = ServiceProvider.of(ChatClient.class).getNewExtension("trae", setting);
        Assertions.assertNotNull(client, "trae ChatClient 扩展未注册：检查 utils-support-trae-starter 的 extensions 文件");
        Assertions.assertFalse(client.models().isEmpty(), "Trae 内置模型表不应为空");
        client.close();
    }

    /**
     * 验证 DuckDuckGo 的 ChatClient 扩展可解析（不触发网络请求）。
     */
    @Test
    void duckDuckGoChatClientExtensionIsResolvable() {
        ChatClientSetting setting = ChatClientSetting.builder().provider("duckduckgo").build();
        ChatClient client = ServiceProvider.of(ChatClient.class).getNewExtension("duckduckgo", setting);
        Assertions.assertNotNull(client, "duckduckgo ChatClient 扩展未注册：检查 utils-support-duckduckgo-starter 依赖");
        client.close();
    }

    /**
     * 验证免登录对话渠道与文生图渠道都已注册，且优先级/能力标记正确。
     */
    @Test
    void channelsAreRegistered() {
        Map<String, ChatChannelProvider> chatChannels = ChannelRegistry.chatChannels();
        Assertions.assertNotNull(chatChannels.get("chat-trae"), "chat-trae 渠道未注册");
        Assertions.assertTrue(chatChannels.get("chat-trae").supportsImage(), "Trae 对话渠道应声明可出图");

        Map<String, ImageChannelProvider> imageChannels = ChannelRegistry.imageChannels();
        Assertions.assertNotNull(imageChannels.get("image-duckduckgo"), "image-duckduckgo 渠道未注册");
        Assertions.assertNotNull(imageChannels.get("image-pollinations"), "image-pollinations 渠道未注册");
        Assertions.assertNotNull(imageChannels.get("image-trae"), "image-trae 渠道未注册");
        Assertions.assertTrue(imageChannels.get("image-pollinations").priority()
                >= imageChannels.get("image-trae").priority(), "免登录出图渠道应排在付费渠道之前");
    }

    /**
     * 验证 Pollinations 渠道的出图地址拼装（不联网，只校验 URL 契约）。
     */
    @Test
    void pollinationsBuildsImageUrlWithoutNetwork() {
        PollinationsImageChannel channel = new PollinationsImageChannel();
        var result = channel.generate("a cat, cinematic lighting", "16:9", 2, 0, 0, "flux");
        Assertions.assertEquals(2, result.images().size());
        for (var image : result.images()) {
            Assertions.assertEquals(1280, image.width());
            Assertions.assertEquals(720, image.height());
            Assertions.assertTrue(image.oriUrl().startsWith("https://image.pollinations.ai/prompt/"));
            Assertions.assertTrue(image.oriUrl().contains("width=1280"));
            Assertions.assertTrue(image.oriUrl().contains("nologo=true"));
            // 空格必须编码为 %20，不能被 URLEncoder 编成 +（+ 在 path 里是字面加号）
            Assertions.assertFalse(image.oriUrl().contains("+"), "提示词空格编码错误");
        }
    }

    /**
     * 验证文生图渠道的模型列表结构（前端下拉依赖 id / name 两个字段）。
     */
    @Test
    void pollinationsExposesModels() {
        List<Map<String, Object>> models = new PollinationsImageChannel().listModels();
        Assertions.assertFalse(models.isEmpty());
        Assertions.assertTrue(models.stream().anyMatch(model -> "flux".equals(model.get("id"))));
    }

    /**
     * 文生图按张计费：请求张数超上限必须在发起网络调用之前被拒绝。
     *
     * <p>该断言不依赖登录态与网络 —— 校验发生在 {@code TraeChatClient#generateImage} 入口，
     * 早于任何 HTTP 请求与费用产生。</p>
     */
    @Test
    void traeImageChannelRejectsTooManyImages() {
        ImageChannelProvider trae = ChannelRegistry.imageChannels().get("image-trae");
        Assertions.assertNotNull(trae, "image-trae 渠道未注册");
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> trae.generate("a cat", "1:1", 50, 0, 0, null));
        Assertions.assertTrue(error.getMessage().contains("单次最多"), error.getMessage());
    }
}