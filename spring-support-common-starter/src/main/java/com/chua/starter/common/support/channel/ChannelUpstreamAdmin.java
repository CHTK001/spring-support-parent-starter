// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.channel;

import java.util.Map;

/**
 * 网关型渠道的「上游 provider」管理 SPI。
 *
 * <p><b>它解决什么</b>：有些渠道本身不是一家上游，而是把多家上游聚合起来的网关
 * （目前是 magpie）。网关内部的上游 provider 由网关自己维护，但用户希望在<b>我们的</b>
 * 渠道页里增删改查，改完再由网关生效。这类能力与具体网关的接口细节强相关，
 * 因此放在渠道实现所在的账号模块。</p>
 *
 * <p><b>为什么参数是凭据 Map</b>：连接记录（{@code sys_ai_provider_connection}）在 AI 模块，
 * 账号模块看不到；消费方（AI）按渠道 + 连接主键取出凭据，再交给实现去调网关。
 * 契约只传不透明 Map，不感知任何渠道的字段形态。</p>
 *
 * <p><b>失败约定</b>：一律抛 {@link ChannelException}，不返回半截结果；
 * 消费方据此把 {@code INVALID_PARAM} / {@code NOT_FOUND} / {@code UPSTREAM_ERROR}
 * 原样交给前端。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ChannelUpstreamAdmin {

    /**
     * 渠道标识，与 {@code @Spi} 别名一致。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 是否管理该渠道。
     *
     * @param channel 渠道标识（可为 null）
     * @return true 表示由本实现管理
     */
    default boolean supports(String channel) {
        return channel != null && channel().equalsIgnoreCase(channel);
    }

    /**
     * 网关地址（展示用）：不发起上游调用，也不抛异常。
     *
     * <p>管理面不可用时前端仍需显示「网关是哪个」，故与 {@link #list} 分开。</p>
     *
     * @param credentials 网关连接凭据
     * @return {@code {baseUrl, pageUrl}}；不支持时返回空 Map
     */
    default Map<String, Object> describe(Map<String, Object> credentials) {
        return Map.of();
    }

    /**
     * 列上游 provider。
     *
     * <p>凭据必须在此脱敏（只留末四位 / 只报数量），明文 key 不得回传前端。</p>
     *
     * @param credentials 网关连接凭据
     * @return {@code {providers:[...]}}；{@code providers} 必然非 null
     * @throws ChannelException 网关不可达 / 管理面未开放 / 页面 Key 无效
     */
    Map<String, Object> list(Map<String, Object> credentials);

    /**
     * 新增 / 覆盖 / 重命名上游 provider。
     *
     * @param credentials 网关连接凭据
     * @param payload     provider 字段（各渠道自定义）
     * @return 上游响应
     * @throws ChannelException 上游调用失败
     */
    Map<String, Object> save(Map<String, Object> credentials, Map<String, Object> payload);

    /**
     * 删除上游 provider。
     *
     * @param credentials 网关连接凭据
     * @param providerId  上游 provider 标识
     * @return 上游响应
     * @throws ChannelException 入参缺失 / 上游调用失败
     */
    Map<String, Object> delete(Map<String, Object> credentials, String providerId);

    /**
     * 上游 provider 的分项只读动作（如 {@code detect} / {@code models} / {@code test}）。
     *
     * <p>白名单由实现维护：写操作必须走 {@link #save} / {@link #delete}，
     * 免得路径段被拼成任意上游调用。</p>
     *
     * @param credentials 网关连接凭据
     * @param action      动作名
     * @param payload     请求体
     * @return 上游响应
     * @throws ChannelException 动作不支持 / 上游调用失败
     */
    Map<String, Object> action(Map<String, Object> credentials, String action, Map<String, Object> payload);
}
