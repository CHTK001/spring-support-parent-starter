// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.channel;

import java.util.List;
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
     * 某个业务渠道在本网关里对应的上游 provider id（按优先级排列）。
     *
     * <p>网关的 provider 命名不一定与我们的渠道 id 相同：magpie 里 Trae 是
     * {@code trae-cn} / {@code trae-global}、MiniMax 是 {@code minimax-code}、
     * Gemini 是 {@code google}。这层映射属于网关自己的知识，消费方（AI 模块）
     * 不应该去猜后缀，也不该维护第二份对照表。</p>
     *
     * <p>默认按同名匹配：provider id 与渠道 id 一致的网关无需覆盖。</p>
     *
     * @param channel 业务渠道标识
     * @return provider id 候选；不管理该渠道时返回空列表
     */
    default List<String> providerIdsOf(String channel) {
        return channel == null || channel.isBlank() ? List.of() : List.of(channel);
    }

    /**
     * 是否支持插件管理（{@link #plugins} / {@link #pluginAction}）。
     *
     * <p>插件是网关自己的扩展机制，不是每个网关型渠道都有。默认 false，
     * 前端据 {@code supportsPlugins} 决定是否显示「插件」页签，而不是猜渠道名。</p>
     *
     * @return true 表示 {@link #plugins} / {@link #pluginAction} 可用
     */
    default boolean supportsPlugins() {
        return false;
    }

    /**
     * 是否支持上游登录（{@link #startSignin} / {@link #signinStatus} / {@link #cancelSignin}）。
     *
     * <p>默认 false；前端据 {@code supportsSignin} 决定是否显示「登录」入口。</p>
     *
     * @return true 表示上游登录可用
     */
    default boolean supportsSignin() {
        return false;
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

    /**
     * 网关的插件清单 / 插件市场。
     *
     * <p>插件是网关侧的扩展（登录、签到、模型目录都可能由插件提供），
     * 与 {@link #list} 的 provider 是两层：provider 是「被登录的上游」，
     * 插件是「提供登录与维护逻辑的包」。默认不支持，网关型渠道按需覆盖。</p>
     *
     * @param credentials 网关连接凭据
     * @param market      true 表示同时读取市场（较慢，可能访问 npm / GitHub）
     * @return {@code {plugins:[...], market:[...]}}；不支持时抛异常
     * @throws ChannelException 不支持 / 网关调用失败
     */
    default Map<String, Object> plugins(Map<String, Object> credentials, boolean market) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持插件管理");
    }

    /**
     * 对网关插件执行动作（安装 / 卸载 / 升级 / 停用 / 改选项 / 换源）。
     *
     * <p>动作白名单由实现维护，避免路径段被拼成任意上游调用。</p>
     *
     * @param credentials 网关连接凭据
     * @param op          动作名
     * @param payload     请求体
     * @return 上游响应
     * @throws ChannelException 动作不支持 / 网关调用失败
     */
    default Map<String, Object> pluginAction(Map<String, Object> credentials, String op,
                                             Map<String, Object> payload) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持插件管理");
    }

    /**
     * 发起一次上游登录（内置 provider 或插件 provider）。
     *
     * <p>返回的登录会话由网关自己持有，凭据不经过本服务；调用方只拿
     * {@code id} 与 {@code url} 去轮询 / 打开授权页。</p>
     *
     * @param credentials 网关连接凭据
     * @param payload     {@code {provider, method, inputs, key, agent, plugin}}
     * @return 登录会话（含 {@code id} / {@code url} / {@code state}）
     * @throws ChannelException 不支持 / 网关调用失败
     */
    default Map<String, Object> startSignin(Map<String, Object> credentials, Map<String, Object> payload) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持上游登录");
    }

    /**
     * 登录前的一问（插件 provider 的 {@code plugin-signin/prompt}）。
     *
     * <p>有的插件登录不是一次调用就能开始：loomy 先要 11 位手机号，其余插件
     * 可能要验证码或选项。调用方按返回的 {@code prompt} 逐题收集答案，把累计
     * {@code inputs} 传回本方法，直到没有下一问再调 {@link #startSignin}。</p>
     *
     * @param credentials 网关连接凭据
     * @param payload     {@code {provider, method, inputs, key?, value?}}
     * @return {@code {prompt, inputs}} 或 {@code {error, inputs}}；不支持的渠道抛异常
     * @throws ChannelException 不支持 / 网关调用失败
     */
    default Map<String, Object> signinPrompt(Map<String, Object> credentials,
                                             Map<String, Object> payload) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持上游登录");
    }

    /**
     * 查询登录会话状态。
     *
     * @param credentials 网关连接凭据
     * @param id          登录会话 id
     * @return 会话状态（{@code state} 为 installing / waiting / done / failed / canceled）
     * @throws ChannelException 不支持 / 会话不存在 / 网关调用失败
     */
    default Map<String, Object> signinStatus(Map<String, Object> credentials, String id) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持上游登录");
    }

    /**
     * 取消登录会话。
     *
     * @param credentials 网关连接凭据
     * @param id          登录会话 id
     * @throws ChannelException 不支持 / 网关调用失败
     */
    default void cancelSignin(Map<String, Object> credentials, String id) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持上游登录");
    }

    /**
     * 读取网关的额度 / 余额卡（管理面）。
     *
     * <p>与积分 SPI 的区别：这里直接返回网关原始卡片（含签到状态、插件账号），
     * 供「上游」页签按 provider / 账号展示，不做单位归并。</p>
     *
     * @param credentials 网关连接凭据
     * @param provider    只取该 provider（可空）
     * @param user        只取该账号（可空，需同时给 provider）
     * @return {@code {quotas:[...]}}
     * @throws ChannelException 不支持 / 网关调用失败
     */
    default Map<String, Object> quotas(Map<String, Object> credentials, String provider, String user) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持额度读取");
    }

    /**
     * 让网关重新读一次额度卡。
     *
     * @param credentials 网关连接凭据
     * @param provider    要刷新的 provider（必填）
     * @param user        要刷新的账号（可空）
     * @return {@code {quotas:[...]}}
     * @throws ChannelException 入参缺失 / 不支持 / 网关调用失败
     */
    default Map<String, Object> refreshQuota(Map<String, Object> credentials, String provider, String user) {
        throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "该渠道不支持额度刷新");
    }
}
