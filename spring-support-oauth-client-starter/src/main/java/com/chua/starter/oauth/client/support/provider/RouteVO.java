package com.chua.starter.oauth.client.support.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;

/**
 * 菜单路由视图对象
 *
 * @author haoxr
 * @since 2020/11/28
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouteVO {

    private String path;

    private String component;

    private String redirect;

    private String name;

    private String status;


    private Boolean hidden;

    private Meta meta;


    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class Params extends HashMap<String, Object> {

    }

    @Data
    public static class Meta {

        private String title;

        private String icon;

        private Boolean hidden;

        private String tag;

        private Boolean affix;
        /**
         * 类型
         */
        private String type;
        /**
         * 颜色
         */
        private String color;

        private List<String> roles;

        private Boolean keepAlive;
        private Params params;
    }

    private List<RouteVO> children;
}
