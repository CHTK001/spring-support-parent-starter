package com.chua.starter.oauth.client.support.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;
/**
 * 路由信息类，用于表示系统的路由配置。
 * 包含路由的路径、组件、重定向、名称、状态、条件、隐藏属性、元信息以及子路由列表。
 *
 * @author haoxr
 * @since 2020/11/28
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouteVO {

    /**
     * 路由的路径，用于URL定位。
     */
    private String path;

    /**
     * 路由对应的组件名称，用于视图渲染。
     */
    private String component;

    /**
     * 路由的重定向目标，用于自动跳转。
     */
    private String redirect;

    /**
     * 路由的名称，用于导航和路由链接。
     */
    private String name;

    /**
     * 路由的状态，用于控制路由的启用或禁用。
     */
    private String status;

    /**
     * 路由的显示条件，用于动态控制路由的可见性。
     */
    private String condition;

    /**
     * 路由是否隐藏，用于菜单和导航的控制。
     */
    private Boolean hidden;

    /**
     * 路由的元信息，包含标题、图标、隐藏、标签、固定标签等信息。
     */
    private Meta meta;

    /**
     * 路由参数类，继承自HashMap，用于存储路由的额外参数。
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Params extends HashMap<String, Object> {

    }

    /**
     * 元信息类，包含路由的标题、图标、隐藏、标签、固定标签、类型、颜色、角色、缓存和参数信息。
     */
    @Data
    public static class Meta {

        /**
         * 路由的标题，用于菜单和导航显示。
         */
        private String title;

        /**
         * 路由的图标，用于菜单和导航的图标显示。
         */
        private String icon;

        /**
         * 路由是否隐藏在菜单和导航中。
         */
        private Boolean hidden;

        /**
         * 路由的标签，用于特殊标识路由。
         */
        private String tag;

        /**
         * 路由的标签是否为固定标签。
         */
        private Boolean affix;
        /**
         * 类型
         */
        private String type;
        /**
         * 颜色
         */
        private String color;

        /**
         * 路由可访问的角色列表，用于权限控制。
         */
        private List<String> roles;

        /**
         * 路由是否应该被缓存。
         */
        private Boolean keepAlive;

        /**
         * 路由的额外参数。
         */
        private Params params;
    }

    /**
     * 路由的子路由列表，用于表示路由的层级关系。
     */
    private List<RouteVO> children;
}
