package com.chua.starter.account;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.spi.ChannelProvider;
import com.chua.starter.account.spi.ChatChannelProvider;
import com.chua.starter.account.spi.ImageChannelProvider;
import com.chua.starter.account.spi.Model3DChannelProvider;
import com.chua.starter.account.spi.VideoChannelProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道注册表门面 —— 收敛「多 SPI root」的枚举逻辑。
 *
 * <p>类型化改造后，渠道实现分布在多个 SPI 注册 root 上：</p>
 * <ul>
 *   <li>免登录对话渠道：{@link ChatChannelProvider}（kind=CHAT，复用 ChatClient SPI）</li>
 *   <li>文生图渠道：{@link ImageChannelProvider}（kind=IMAGE，复用 ChatClient#generateImage）</li>
 *   <li>文生视频渠道：{@link VideoChannelProvider}（kind=VIDEO，复用 ChatClient#generateVideo）</li>
 *   <li>图生 3D 渠道：{@link Model3DChannelProvider}（kind=MODEL_3D，复用 ChatClient#generate3D）</li>
 * </ul>
 *
 * <p>需要登录态的 AI 账号渠道（{@code AccountChannelAuthManager}）属于 AI 模块自身能力，
 * 不在此注册表中登记；上层若需要，自行按 SPI 枚举，本模块不反向依赖 AI。</p>
 *
 * <p>上层（列表接口）只依赖本门面，不直接感知「有几个 root」，
 * 后续新增渠道种类时改动面收敛于此。</p>
 *
 * <p>⚠️ 去重键取实现自己的 {@link ChannelProvider#channel()}，不能用注册表的键：
 * SPI 框架会给每个实现额外登记一条「类简名」definition，与 {@code @Spi} 别名指向同一实例，
 * 用注册表键去重会重复。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class ChannelRegistry {

    /**
     * 工具类，禁止实例化。
     */
    private ChannelRegistry() {
    }

    /**
     * 全部免登录对话渠道实现（按 {@code channel()} 去重，保持注册顺序）。
     *
     * @return 渠道标识 → ChatClient 渠道实现
     */
    public static Map<String, ChatChannelProvider> chatChannels() {
        return dedup(ServiceProvider.of(ChatChannelProvider.class).list());
    }

    /**
     * 按渠道标识取免登录对话渠道（大小写不敏感）。
     *
     * @param channel 渠道标识
     * @return 渠道实现；未注册时返回 {@code null}
     */
    public static ChatChannelProvider chatOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        Map<String, ChatChannelProvider> channels = chatChannels();
        ChatChannelProvider matched = channels.get(channel);
        if (matched != null) {
            return matched;
        }
        for (ChatChannelProvider provider : channels.values()) {
            if (provider != null && provider.channel() != null
                    && provider.channel().equalsIgnoreCase(channel)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 全部文生图渠道实现（按 {@code channel()} 去重，保持注册顺序）。
     *
     * @return 渠道标识 → 文生图渠道实现
     */
    public static Map<String, ImageChannelProvider> imageChannels() {
        return dedup(ServiceProvider.of(ImageChannelProvider.class).list());
    }

    /**
     * 按渠道标识取文生图渠道（大小写不敏感）。
     *
     * @param channel 渠道标识
     * @return 渠道实现；未注册时返回 {@code null}
     */
    public static ImageChannelProvider imageOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        Map<String, ImageChannelProvider> channels = imageChannels();
        ImageChannelProvider matched = channels.get(channel);
        if (matched != null) {
            return matched;
        }
        for (ImageChannelProvider provider : channels.values()) {
            if (provider != null && provider.channel() != null
                    && provider.channel().equalsIgnoreCase(channel)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 全部文生视频渠道实现（按 {@code channel()} 去重）。
     *
     * @return 渠道标识 → 文生视频渠道实现
     */
    public static Map<String, VideoChannelProvider> videoChannels() {
        return dedup(ServiceProvider.of(VideoChannelProvider.class).list());
    }

    /**
     * 按渠道标识取文生视频渠道（大小写不敏感）。
     *
     * @param channel 渠道标识
     * @return 渠道实现；未注册时返回 {@code null}
     */
    public static VideoChannelProvider videoOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        Map<String, VideoChannelProvider> channels = videoChannels();
        VideoChannelProvider matched = channels.get(channel);
        if (matched != null) {
            return matched;
        }
        for (VideoChannelProvider provider : channels.values()) {
            if (provider != null && provider.channel() != null
                    && provider.channel().equalsIgnoreCase(channel)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 全部图生 3D 渠道实现（按 {@code channel()} 去重）。
     *
     * @return 渠道标识 → 图生 3D 渠道实现
     */
    public static Map<String, Model3DChannelProvider> model3dChannels() {
        return dedup(ServiceProvider.of(Model3DChannelProvider.class).list());
    }

    /**
     * 按渠道标识取图生 3D 渠道（大小写不敏感）。
     *
     * @param channel 渠道标识
     * @return 渠道实现；未注册时返回 {@code null}
     */
    public static Model3DChannelProvider model3dOf(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        Map<String, Model3DChannelProvider> channels = model3dChannels();
        Model3DChannelProvider matched = channels.get(channel);
        if (matched != null) {
            return matched;
        }
        for (Model3DChannelProvider provider : channels.values()) {
            if (provider != null && provider.channel() != null
                    && provider.channel().equalsIgnoreCase(channel)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 按实现自己的 {@code channel()} 去重（SPI 注册表含类简名别名，需剔除重复实例）。
     *
     * @param raw 原始注册表
     * @param <T> 渠道类型
     * @return 去重后的注册表
     */
    private static <T extends ChannelProvider> Map<String, T> dedup(Map<String, T> raw) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T provider : raw.values()) {
            if (provider == null || provider.channel() == null) {
                continue;
            }
            result.putIfAbsent(provider.channel(), provider);
        }
        return result;
    }
}
