package com.chua.starter.oauth.client.support.provider;

import java.util.List;
import java.util.Set;

/**
 * 用户菜单结果
 *
 * @param menu 用户菜单
 * @param permissions 权限
 * @param dashboardGrid 用户所拥有的组件
 * @param dashboard 首页样式
 *
 * @author CH
 * @since 4.0.0
 */
public record UserMenuResult(
        List<RouteVO> menu,
        Set<String> permissions,
        Set<String> dashboardGrid,
        String dashboard
) {
}
