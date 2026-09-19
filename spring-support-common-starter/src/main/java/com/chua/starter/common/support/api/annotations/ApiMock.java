package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API Mock 数据注解
 * <p>
 * 用于在开发/测试环境返回 Mock 数据，生产环境自动禁用。
 * </p>
 *
 * <h3>处理优先级：3</h3>
 * <p>
 * 优先级顺序：@api内部(1) &gt; @api特征(2) &gt; @apimock(3) &gt; @api已弃用(4) &gt; @apigray(5)
 * </p>
 * <p>
 * 处理阶段：拦截器阶段（请求进入前）。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;ApiMock(profile = "dev", responseFile = "mock/users.json")
 * &#64;GetMapping("/users")
 * public List&lt;User&gt; getUsers() { ... }
 *
 * // 或者直接返回 JSON 字符串
 * &#64;ApiMock(profile = "dev", response = "{\"code\": 0, \"msg\": \"success\"}")
 * &#64;GetMapping("/test")
 * public Result test() { ... }
 * </pre>
 *
 * @since 2024/12/08
 * @author CH
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiMock {

    /**
     * 生效的环境（spring.配置文件.活跃）
     * <p>
     * 多个环境用逗号分隔，如 "dev,测试"
     * </p>
     *
     * @return 环境列表，默认 dev
     */
    String[] profile() default {"dev"};

    /**
     * Mock 响应文件路径（类路径 下）
     * <p>
     * 优先级低于 响应，如果 响应 有值则使用 响应
     * </p>
     *
     * @return 文件路径
     */
    String responseFile() default "";

    /**
     * Mock 响应内容（JSON 字符串）
     * <p>
     * 优先级高于 响应文件
     * </p>
     *
     * @return JSON 响应内容
     */
    String response() default "";

    /**
     * 响应状态码
     *
     * @return HTTP 状态码
     */
    int status() default 200;

    /**
     * 响应 内容-类型
     *
     * @return Content-Type
     */
    String contentType() default "application/json;charset=UTF-8";

    /**
     * 模拟延迟时间（毫秒）
     * <p>
     * 用于模拟网络延迟
     * </p>
     *
     * @return 延迟毫秒数
     */
    long delay() default 0;

    /**
     * Mock 描述
     *
     * @return 描述信息
     */
    String description() default "";
}
