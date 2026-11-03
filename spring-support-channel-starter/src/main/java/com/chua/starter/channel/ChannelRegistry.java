package com.chua.starter.channel;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import com.chua.starter.channel.spi.ChannelKind;
import com.chua.starter.channel.spi.ChannelProvider;
import com.chua.starter.channel.spi.ChatChannelProvider;
import com.chua.starter.channel.spi.TemporaryAccountProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道注册表门面 —— 收敛「多 SPI root」的枚举逻辑。
 *
 * <p>类型化改造后，渠道实现分布在三个 SPI 注册 root 上：</p>
 * <ul>
 *   <li>AI 渠道：{@code com.chua.starter.ai.support.spi.AccountChannelAuthManager}（kind=AI，固定账号）</li>
 *   <li>免登录对话渠道：{@link ChatChannelProvider}（kind=CHAT，复用 ChatClient SPI）</li>
 *   <li>短信 / 邮件渠道：{@link TemporaryAccountProvider}（临时账号）</li>
 * </ul>
 *
 * <p>上层（列表接口、临时账号编排）只依赖本门面，不直接感知「有几个 root」，
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
     * 全部 AI 渠道实现（按 {@code channel()} 去重，保持注册顺序）。
     *
     * @return 渠道标识 → AI 渠道实现
     */
    public static Map<String, AccountChannelAuthManager> aiChannels() {
        return dedup(ServiceProvider.of(AccountChannelAuthManager.class).list());
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
     * 全部临时账号渠道实现（短信 / 邮件，按 {@code channel()} 去重）。
     *
     * @return 渠道标识 → 临时账号渠道实现
     */
    public static Map<String, TemporaryAccountProvider> temporaryChannels() {
        return dedup(ServiceProvider.of(TemporaryAccountProvider.class).list());
    }

    /**
     * 按种类取临时账号渠道。
     *
     * <p>返回值按 {@link ChannelProvider#priority()} <b>降序</b>排列：数值越大越优先，
     * 同优先级保持 SPI 注册顺序。编排层取第一个即「进入优先」的数据源。</p>
     *
     * @param kind 渠道种类（{@link ChannelKind#SMS} / {@link ChannelKind#EMAIL}）
     * @return 匹配的渠道列表（优先级降序）
     */
    public static List<TemporaryAccountProvider> temporaryOf(ChannelKind kind) {
        List<TemporaryAccountProvider> result = new ArrayList<>();
        for (TemporaryAccountProvider provider : temporaryChannels().values()) {
            if (provider != null && provider.kind() == kind) {
                result.add(provider);
            }
        }
        result.sort(Comparator.comparingInt(ChannelProvider::priority).reversed());
        return result;
    }

    /**
     * 取指定种类的第一个临时账号渠道（优先级最高的默认数据源）。
     *
     * @param kind 渠道种类
     * @return 默认渠道；无匹配时返回 {@code null}
     */
    public static TemporaryAccountProvider firstTemporaryOf(ChannelKind kind) {
        List<TemporaryAccountProvider> list = temporaryOf(kind);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 按「指定渠道优先 + 优先级降级」取临时账号渠道。
     *
     * <p>用于临时身份创建：前端选定了渠道就用它（必须与 kind 匹配且已注册），
     * 未指定或不可用时回落到该种类中优先级最高的渠道。</p>
     *
     * @param kind    渠道种类
     * @param channel 期望的渠道标识（可空）
     * @return 渠道实现；该种类没有任何实现时返回 {@code null}
     */
    public static TemporaryAccountProvider resolveTemporary(ChannelKind kind, String channel) {
        if (channel != null && !channel.isBlank()) {
            TemporaryAccountProvider expected = temporaryChannels().get(channel);
            if (expected != null && expected.kind() == kind) {
                return expected;
            }
        }
        return firstTemporaryOf(kind);
    }

    /**
     * 全部渠道（AI + 免登录对话 + 短信 + 邮件），供列表接口按种类打标。
     *
     * @return 渠道实现列表
     */
    public static List<ChannelProvider> all() {
        List<ChannelProvider> result = new ArrayList<>();
        result.addAll(aiChannels().values());
        result.addAll(chatChannels().values());
        result.addAll(temporaryChannels().values());
        return result;
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
