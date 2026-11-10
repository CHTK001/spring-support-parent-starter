// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.channel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关型渠道内部的<b>上游 provider 级</b>适配 SPI。
 *
 * <p><b>它解决什么</b>：{@link ChannelUpstreamAdmin} 管的是「一个网关」，
 * 但网关里的每个上游（Qoder / WorkBuddy / MiniMax …）返回的模型字段、
 * 账号标识、额度结构并不一致。把这些差异塞进网关实现的 if 分支，
 * 会随着上游数量增长变成一坨；这里按 provider 再分一层，每个上游一个实现。</p>
 *
 * <p><b>和上层 SPI 的关系</b>：渠道级 SPI（{@code AccountChannelAuthManager} /
 * {@code ChannelCreditProvider} / {@code ChannelUpstreamAdmin}）按渠道别名取；
 * 本接口由渠道实现按 provider id 取，取不到时回落通用逻辑。
 * 这是两层，不再往下套第三层。</p>
 *
 * <p><b>默认实现</b>：{@link #modelMeta(Map)} 返回空 Map，表示上游字段已经
 * 是标准形态、无需翻译。只有真正有差异的 provider 才实现本接口。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface ChannelUpstreamProvider {

    /**
     * 所属网关渠道标识，与 {@code @Spi} 别名一致。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 是否由本实现处理该 provider。
     *
     * @param providerId 网关里的 provider id（可为 null）
     * @return true 表示由本实现处理
     */
    default boolean supports(String providerId) {
        return true;
    }

    /**
     * 把上游返回的单个模型对象翻译成渠道管理页认识的字段。
     *
     * <p>只返回**确实读到**的键，缺值不补 0：价格 / 免费 / 倍率在上游没给时，
     * 前端必须能区分「免费」与「不知道」。</p>
     *
     * <p>约定键：{@code contextWindow}（整数）、{@code free}（布尔）、
     * {@code priceInput} / {@code priceOutput}（每百万 token 的价格）、
     * {@code rate}（倍率）。其余键原样透传也可以，消费方忽略不认识的键。</p>
     *
     * @param model 上游模型对象（可能是 {@code {id,name}} 或带价格字段的完整对象）
     * @return 标准字段（可为空 Map）
     */
    default Map<String, Object> modelMeta(Map<String, Object> model) {
        return new LinkedHashMap<>(0);
    }
}
