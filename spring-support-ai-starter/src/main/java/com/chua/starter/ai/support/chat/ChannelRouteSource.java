// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.route.RouteTarget;

import java.util.Map;

/**
 * 免费渠道路由源：往「模型名 → 上游目标」这张表里贡献自己那份。
 *
 * <p>免费渠道（{@code free-channel}）是渠道管理的统一入口，它的路由来自多个互不相干的
 * 东西：OAuth 账号渠道（{@code sys_ai_provider_model} 登记的模型）、免登录对话渠道
 * （DuckDuckGo / Trae 的内置模型）、magpie 网关（网关自己维护的动态模型目录），
 * 将来还会有新的来源。把它们写成一个接口，{@link ModelClientResolver} 只负责
 * 「按优先级叠加」，新增来源不必再改路由收集逻辑。</p>
 *
 * <p><b>优先级</b>：{@link #order()} 小的先叠加；{@link #fallbackOnly()} 为
 * {@code true} 的源只在空位上补（{@code putIfAbsent}），不会盖掉显式登记过的模型名——
 * 网关与免登录渠道的模型名可能和真实上游撞名，撞上了必须走后者。</p>
 *
 * <p>实现必须无副作用且可重复调用：路由收集会按 ownerScope 缓存 5 分钟，
 * 缓存失效后随时重新调用。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface ChannelRouteSource {

    /**
     * 源名称（日志与排障用）。
     *
     * @return 源名称
     */
    String name();

    /**
     * 叠加顺序（越小越先叠加，先叠加者优先）。
     *
     * @return 顺序值
     */
    default int order() {
        return 0;
    }

    /**
     * 是否只做兜底（只在空位上补，不覆盖已有路由）。
     *
     * @return {@code true}=兜底源
     */
    default boolean fallbackOnly() {
        return false;
    }

    /**
     * 当前作用域下本源的模型路由。
     *
     * @param ownerScope 归属作用域（{@code _free} / {@code _sys} / 用户标识）
     * @return 模型名 → 路由目标；无可用路由时返回空 Map，不要返回 {@code null} 之外再抛错
     */
    Map<String, RouteTarget> routes(String ownerScope);
}
