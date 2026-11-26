package com.chua.starter.ai.support.util;

import com.chua.common.support.ai.chat.route.RouteTarget;
import java.util.Map;

/**
 * 路由配置兼容入口，渠道配置解释统一由账号模块维护。
 * @author CH
 */
public final class ProviderRouteTargets {
    /** 免费渠道标识。 */
    public static final String FREE_CHANNEL_PLATFORM = com.chua.starter.account.channel.support.ProviderRouteTargets.FREE_CHANNEL_PLATFORM;
    /** 免费渠道别名。 */
    public static final String FREE_CHANNEL_ALIAS = com.chua.starter.account.channel.support.ProviderRouteTargets.FREE_CHANNEL_ALIAS;
    /** 路由组标识。 */
    public static final String ROUTER_GROUP_PLATFORM = com.chua.starter.account.channel.support.ProviderRouteTargets.ROUTER_GROUP_PLATFORM;
    private ProviderRouteTargets() { }
    /** @param platform 平台 @return 是否免费渠道 */
    public static boolean isFreeChannel(String platform) {
        return com.chua.starter.account.channel.support.ProviderRouteTargets.isFreeChannel(platform);
    }
    /** @param platform 平台 @return 是否路由组 */
    public static boolean isRouterGroup(String platform) {
        return com.chua.starter.account.channel.support.ProviderRouteTargets.isRouterGroup(platform);
    }
    /** @param extra 渠道配置 @return 路由目标 */
    public static Map<String, RouteTarget> fromExtra(Map<String, Object> extra) {
        return com.chua.starter.account.channel.support.ProviderRouteTargets.fromExtra(extra);
    }
}
