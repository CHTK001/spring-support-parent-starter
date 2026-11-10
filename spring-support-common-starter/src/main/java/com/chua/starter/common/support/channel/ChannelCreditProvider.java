package com.chua.starter.common.support.channel;

import java.util.List;
import java.util.Map;

/**
 * 账号类 AI 渠道的<b>积分 / 余额查询</b> SPI。
 *
 * <p>与 {@link AccountChannelAuthManager#checkin(Map)} 的分工：签到是「动作」（每天领一次），
 * 积分是「读数」（随时可查、可展示、可用于额度预警）。很多渠道只有其一：</p>
 * <ul>
 *   <li>只有读数：Cline、MiniMax、Gemini（额度按窗口重置，没有签到端点）；</li>
 *   <li>只有动作：Raccoon（每日积分服务端自动发放）；</li>
 *   <li>两者都有：WorkBuddy、Trae、Qoder、LobsterAI。</li>
 * </ul>
 *
 * <p>本接口独立于 {@link AccountChannelAuthManager}：渠道管理页需要在<b>不触发签到</b>的前提下
 * 展示余额（否则「看一眼余额」就等于「签到一次」），二者必须能分别调用。</p>
 *
 * <p><b>返回契约</b>（字段全部可空，缺省表示「上游未提供」，不是 0）：</p>
 * <ul>
 *   <li>{@code remain} / {@code total}：剩余 / 总量；{@code total < 0} 表示「不限量」；</li>
 *   <li>{@code unit}：单位，{@code credit}（积分）/ {@code token} / {@code currency}；</li>
 *   <li>{@code currency}：{@code unit=currency} 时的币种（ISO 4217）；</li>
 *   <li>{@code exceeded}：上游明确标记「已超额」；</li>
 *   <li>{@code plan}：套餐 / 计划名；</li>
 *   <li>{@code expiresAt}：最近一个权益包的到期时刻（毫秒）；</li>
 *   <li>{@code updatedAt}：本次读数时刻（毫秒）；</li>
 *   <li>{@code packages}：明细列表，每项 {@code {name, remain, size, expiresAt}}；</li>
 *   <li>{@code error}：查询失败原因（此时 {@code remain} 缺失，调用方应展示「未知」而不是 0）。</li>
 * </ul>
 *
 * <p>实现类应<b>永不抛异常</b>：查询失败写 {@code error} 返回，因为余额是展示性信息，
 * 失败不该让整个渠道列表报错。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public interface ChannelCreditProvider {

    /**
     * 渠道标识（与 {@link AccountChannelAuthManager#channel()} 一致）。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 是否支持积分 / 余额查询。
     *
     * @return true 表示支持
     */
    default boolean supportsCredit() {
        return true;
    }

    /**
     * 查询当前账号的积分 / 余额。
     *
     * @param credentials 账号凭据（来自连接表 credentials 字段）
     * @return 积分快照（字段见类注释）；不支持或失败时返回含 {@code error} 的 Map
     */
    Map<String, Object> credit(Map<String, Object> credentials);

    /**
     * 按业务渠道查询积分 / 余额（网关托管渠道用）。
     *
     * <p>网关把多家上游聚在一个连接里，凭据是<b>网关的</b>；要回答「Qoder 还剩多少」
     * 就必须按上游过滤。默认实现忽略 {@code vendor}，等价于 {@link #credit(Map)}。</p>
     *
     * @param vendor      业务渠道标识（决定取哪一家上游的额度）
     * @param credentials 网关连接凭据
     * @return 积分快照（字段见类注释）
     */
    default Map<String, Object> creditFor(String vendor, Map<String, Object> credentials) {
        return credit(credentials);
    }

    /**
     * 该渠道的积分单位（供前端渲染，如「积分」「Token」「USD」）。
     *
     * @return 单位名
     */
    default String creditUnit() {
        return "credit";
    }

    /**
     * 积分明细（可选）：默认取 {@link #credit(Map)} 的 {@code packages}。
     *
     * @param credentials 账号凭据
     * @return 明细列表
     */
    default List<Map<String, Object>> creditPackages(Map<String, Object> credentials) {
        Object packages = credit(credentials).get("packages");
        if (packages instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        Map<?, ?> raw = (Map<?, ?>) item;
                        Map<String, Object> row = new java.util.LinkedHashMap<>(raw.size());
                        raw.forEach((k, v) -> row.put(String.valueOf(k), v));
                        return row;
                    })
                    .toList();
        }
        return List.of();
    }
}
