package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API 功能开关注解
 * <p>
 * 用于基于功能开关动态启用/禁用 API 接口，支持运行时热切换。
 * </p>
 *
 * <h3>处理优先级：2</h3>
 * <p>
 * 在拦截器阶段处理，优先级仅次于 @ApiInternal。
 * 处理阶段：拦截器阶段（请求进入前）。
 * </p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>支持运行时动态开启/关闭接口</li>
 *   <li>支持默认开关状态配置</li>
 *   <li>支持通过管理接口远程控制</li>
 *   <li>支持功能分组管理</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 标记功能开关，默认开启
 * &#64;ApiFeature("new-search")
 * &#64;GetMapping("/search/v2")
 * public Result newSearch() { ... }
 *
 * // 标记功能开关，默认关闭
 * &#64;ApiFeature(value = "beta-feature", defaultEnabled = false)
 * &#64;GetMapping("/beta")
 * public Result betaFeature() { ... }
 *
 * // 分组管理
 * &#64;ApiFeature(value = "user-export", group = "user-module")
 * &#64;GetMapping("/users/export")
 * public Result exportUsers() { ... }
 * </pre>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiFeature {

    /**
     * 功能开关标识
     * <p>
     * 用于唯一标识一个功能开关
     * </p>
     *
     * @return 功能标识
     */
    String value();

    /**
     * 功能描述
     *
     * @return 描述信息
     */
    String description() default "";

    /**
     * 功能分组
     * <p>
     * 用于对功能进行分组管理
     * </p>
     *
     * @return 分组名称
     */
    String group() default "default";

    /**
     * 默认开关状态
     *
     * @return 默认是否开启
     */
    boolean defaultEnabled() default true;

    /**
     * 关闭时的响应消息
     *
     * @return 响应消息
     */
    String disabledMessage() default "此功能暂未开放";

    /**
     * 关闭时的响应状态码
     *
     * @return HTTP 状态码
     */
    int disabledStatus() default 503;
}
