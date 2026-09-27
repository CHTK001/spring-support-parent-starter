package com.chua.starter.oauth.client.support.provider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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

    /**
     * 规范构造器：对菜单与组件集合做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。权限集合保留可空语义：
     * 上游用户信息尚未填充权限时为 {@code null}，此处不做空值敌对化。</p>
     *
     * @param menu          用户菜单
     * @param permissions   权限
     * @param dashboardGrid 用户所拥有的组件
     */
    public UserMenuResult {
        menu = List.copyOf(Objects.requireNonNull(menu, "menu 不能为 null"));
        permissions = permissions == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        dashboardGrid = Set.copyOf(Objects.requireNonNull(dashboardGrid, "dashboardGrid 不能为 null"));
    }
}
