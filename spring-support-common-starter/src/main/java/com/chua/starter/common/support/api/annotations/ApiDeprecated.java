package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API 废弃标记注解
 * <p>
 * 用于标记即将废弃的 API 接口，支持版本控制和替代接口提示。
 * 支持语义化版本号（如 1.0.0, 1.0.0-release, 2.0.0-rc.1）。
 * </p>
 *
 * <h3>处理优先级：4</h3>
 * <p>
 * 优先级顺序：@ApiInternal(1) &gt; @ApiFeature(2) &gt; @ApiMock(3) &gt; @ApiDeprecated(4) &gt; @ApiGray(5)
 * </p>
 * <p>
 * 处理阶段：拦截器阶段（请求进入前）。
 * </p>
 *
 * <h3>废弃规则</h3>
 * <ul>
 *   <li>当请求版本高于 since 版本时，如果配置了 replacement，返回 301 重定向提示</li>
 *   <li>当请求版本高于 since 版本且未配置 replacement，返回空结果</li>
 *   <li>响应头中添加 X-API-Deprecated 警告信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 标记从 v2.0 开始废弃，推荐使用新接口
 * &#64;ApiDeprecated(since = "2.0", replacement = "/v2/users", message = "请使用新版用户接口")
 * &#64;GetMapping("/users")
 * public List&lt;User&gt; getUsers() { ... }
 *
 * // 标记废弃，未配置替代接口，高版本请求返回空
 * &#64;ApiDeprecated(since = "3.0")
 * &#64;GetMapping("/old-api")
 * public Result oldApi() { ... }
 * </pre>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiDeprecated {

    /**
     * 废弃起始版本
     * <p>
     * 从此版本开始标记为废弃
     * </p>
     *
     * @return 废弃版本号
     */
    String since();

    /**
     * 替代接口路径
     * <p>
     * 如果配置了替代接口，请求高于 since 版本时返回 301 提示；
     * 未配置则返回空结果
     * </p>
     *
     * @return 替代接口路径
     */
    String replacement() default "";

    /**
     * 废弃提示信息
     *
     * @return 提示信息
     */
    String message() default "此接口已废弃";

    /**
     * 完全移除的版本
     * <p>
     * 超过此版本的请求将直接返回 410 Gone
     * </p>
     *
     * @return 移除版本号，默认空表示不移除
     */
    String removedIn() default "";

    /**
     * 是否在响应头中添加废弃警告
     *
     * @return 是否添加警告头
     */
    boolean addWarningHeader() default true;
}
