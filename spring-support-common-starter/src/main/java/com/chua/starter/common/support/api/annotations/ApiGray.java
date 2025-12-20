package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API 灰度发布注解
 * <p>
 * 用于实现接口的灰度发布，支持按规则将部分流量路由到新版本接口。
 * </p>
 *
 * <h3>处理优先级：5</h3>
 * <p>
 * 优先级顺序：@ApiInternal(1) &gt; @ApiFeature(2) &gt; @ApiMock(3) &gt; @ApiDeprecated(4) &gt; @ApiGray(5)
 * </p>
 * <p>
 * 处理阶段：拦截器阶段（请求进入前）。
 * </p>
 *
 * <h3>灰度策略</h3>
 * <ul>
 *   <li><strong>百分比灰度</strong>: 按指定比例随机分配流量</li>
 *   <li><strong>规则灰度</strong>: 基于 SpEL 表达式匹配请求</li>
 *   <li><strong>白名单灰度</strong>: 指定用户/IP 进入灰度</li>
 * </ul>
 *
 * <h3>上下文变量（SpEL 表达式可用）</h3>
 * <ul>
 *   <li>#userId - 当前用户ID</li>
 *   <li>#username - 当前用户名</li>
 *   <li>#ip - 客户端IP</li>
 *   <li>#header('name') - 请求头</li>
 *   <li>#param('name') - 请求参数</li>
 *   <li>#cookie('name') - Cookie值</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 百分比灰度：10% 流量进入灰度
 * &#64;ApiGray(percentage = 10)
 * &#64;GetMapping("/search")
 * public Result searchV2() { ... }
 *
 * // 规则灰度：用户ID尾号为0的进入灰度
 * &#64;ApiGray(rule = "#userId % 10 == 0")
 * &#64;GetMapping("/recommend")
 * public Result recommendV2() { ... }
 *
 * // 白名单灰度：指定用户进入灰度
 * &#64;ApiGray(users = {"admin", "test"})
 * &#64;GetMapping("/beta")
 * public Result betaFeature() { ... }
 *
 * // 组合使用：白名单优先，然后按百分比
 * &#64;ApiGray(users = {"vip"}, percentage = 20)
 * &#64;GetMapping("/new-feature")
 * public Result newFeature() { ... }
 * </pre>
 *
 * @author CH
 * @since 2024/12/18
 * @version 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiGray {

    /**
     * 灰度版本标识
     * <p>
     * 用于区分不同的灰度版本
     * </p>
     *
     * @return 版本标识
     */
    String value() default "";

    /**
     * 灰度描述
     *
     * @return 描述信息
     */
    String description() default "";

    /**
     * 灰度百分比（0-100）
     * <p>
     * 设置后将按指定百分比随机分配流量到此接口。
     * 设置为 0 表示不使用百分比灰度。
     * </p>
     *
     * @return 百分比，默认 0
     */
    int percentage() default 0;

    /**
     * 灰度规则（SpEL 表达式）
     * <p>
     * 返回 true 时请求进入灰度版本。
     * 支持的上下文变量：
     * <ul>
     *   <li>#userId - 当前用户ID</li>
     *   <li>#username - 当前用户名</li>
     *   <li>#ip - 客户端IP</li>
     *   <li>#header('name') - 请求头</li>
     *   <li>#param('name') - 请求参数</li>
     *   <li>#cookie('name') - Cookie值</li>
     * </ul>
     * </p>
     *
     * @return SpEL 表达式
     */
    String rule() default "";

    /**
     * 灰度用户白名单
     * <p>
     * 指定用户名直接进入灰度版本
     * </p>
     *
     * @return 用户名列表
     */
    String[] users() default {};

    /**
     * 灰度IP白名单
     * <p>
     * 指定IP直接进入灰度版本，支持通配符（如 192.168.1.*）
     * </p>
     *
     * @return IP列表
     */
    String[] ips() default {};

    /**
     * 灰度请求头匹配
     * <p>
     * 指定请求头存在时进入灰度版本。
     * 格式：headerName 或 headerName=value
     * </p>
     *
     * @return 请求头配置
     */
    String[] headers() default {};

    /**
     * 未命中灰度时的降级接口路径
     * <p>
     * 当请求未命中灰度规则时，重定向到此接口。
     * 如果为空，则返回灰度未开放响应。
     * </p>
     *
     * @return 降级接口路径
     */
    String fallback() default "";

    /**
     * 未命中灰度时的响应消息
     *
     * @return 响应消息
     */
    String notInGrayMessage() default "您暂未被邀请体验此功能";

    /**
     * 未命中灰度时的响应状态码
     *
     * @return HTTP 状态码
     */
    int notInGrayStatus() default 200;

    /**
     * 是否强制灰度（必须命中规则才能访问）
     * <p>
     * true: 未命中灰度规则时拒绝访问
     * false: 未命中灰度规则时正常执行（默认）
     * </p>
     *
     * @return 是否强制灰度
     */
    boolean forceGray() default false;

    /**
     * 灰度分组
     * <p>
     * 用于对灰度进行分组管理
     * </p>
     *
     * @return 分组名称
     */
    String group() default "default";
}
