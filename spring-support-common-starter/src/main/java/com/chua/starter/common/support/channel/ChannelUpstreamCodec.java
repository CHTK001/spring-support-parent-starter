// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.channel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关型渠道的 provider「线上形态」适配 SPI。
 *
 * <p><b>它解决什么</b>：网关的列表接口与写接口对同一个 provider 的字段形态并不一致。
 * 典型例子是 magpie：列表把 {@code balanceToken} 下发成 {@code {takes,set}} 说明对象、
 * 把 {@code models} 下发成对象数组、把 {@code key} 脱敏成 {@code ****abcd}，
 * 而 {@code /api/provider/*} 的输入结构要求前两者是字符串 / 字符串数组，且回传脱敏 key
 * 会让动作拿假凭据去测试。前端把列表行原样回传是最自然的写法，因此「列表 -> 表单」
 * 与「表单 -> 上游输入」的翻译必须由渠道自己负责。</p>
 *
 * <p><b>为什么做成 SPI</b>：这是渠道私有的知识，不属于 AI 模块的控制器，也不该散落在
 * 各个 {@link ChannelUpstreamAdmin} 的 if 分支里。每个网关实现自己的 Codec，消费者按
 * 渠道别名取用；新增网关只要加一个实现，不用改公共代码。</p>
 *
 * <p><b>默认实现</b>：不做任何翻译，只做防御性复制。没有特殊字段形态的网关可以完全不实现。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface ChannelUpstreamCodec {

    /**
     * 渠道标识，与 {@code @Spi} 别名一致。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 是否适配该渠道。
     *
     * @param channel 渠道标识（可为 null）
     * @return true 表示由本实现适配
     */
    default boolean supports(String channel) {
        return channel != null && channel().equalsIgnoreCase(channel);
    }

    /**
     * 把网关列表下发的 provider 转成可以安全交给前端的视图。
     *
     * <p>实现必须保证凭据不外泄（只留末四位 / 只报数量）。</p>
     *
     * @param provider 网关原始 provider
     * @return 脱敏后的副本
     */
    default Map<String, Object> desensitize(Map<String, Object> provider) {
        return provider == null ? new LinkedHashMap<>() : new LinkedHashMap<>(provider);
    }

    /**
     * 把前端回传的 provider 转成网关写接口 / 动作接口能接受的输入。
     *
     * <p>列表接口的派生物（展示字段、对象形态）必须在这里清掉或归一，
     * 否则上游会以结构错误拒绝。</p>
     *
     * @param payload 前端请求体或其中 {@code provider} 字段
     * @return 可发送给网关的副本
     */
    default Map<String, Object> toUpstreamInput(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }
}
