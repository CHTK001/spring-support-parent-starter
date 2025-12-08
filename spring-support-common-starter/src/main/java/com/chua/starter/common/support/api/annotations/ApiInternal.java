package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API 内部接口注解
 * <p>
 * 标记接口为内部接口，仅允许内网IP或白名单中的IP调用。
 * 内部接口不受 OAuth 鉴权控制。
 * </p>
 *
 * <h3>处理优先级：1（最高）</h3>
 * <p>
 * 在所有 API 控制注解中优先级最高，最先处理。
 * 处理阶段：拦截器阶段（请求进入前）。
 * </p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动识别内网IP（192.168.x.x, 10.x.x.x, 172.16-31.x.x, 127.0.0.1）</li>
 *   <li>支持自定义IP白名单</li>
 *   <li>支持服务名白名单</li>
 *   <li>自动跳过 OAuth 鉴权</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 仅允许内网IP调用
 * &#64;ApiInternal
 * &#64;GetMapping("/internal/data")
 * public Result getData() { ... }
 *
 * // 指定IP白名单
 * &#64;ApiInternal(allowedIps = {"192.168.1.100", "10.0.0.50"})
 * &#64;PostMapping("/internal/notify")
 * public void notify() { ... }
 *
 * // 指定服务名白名单
 * &#64;ApiInternal(allowedServices = {"order-service", "payment-service"})
 * &#64;PostMapping("/internal/callback")
 * public void callback() { ... }
 * </pre>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiInternal {

    /**
     * 允许的IP地址白名单
     * <p>
     * 支持精确IP和CIDR格式
     * </p>
     *
     * @return IP白名单列表
     */
    String[] allowedIps() default {};

    /**
     * 允许的服务名白名单
     * <p>
     * 通过请求头 X-Service-Name 识别调用方服务
     * </p>
     *
     * @return 服务名白名单列表
     */
    String[] allowedServices() default {};

    /**
     * 是否允许所有内网IP
     * <p>
     * 默认允许内网IP（192.168.x.x, 10.x.x.x, 172.16-31.x.x, 127.0.0.1）
     * </p>
     *
     * @return 是否允许内网IP
     */
    boolean allowPrivateNetwork() default true;

    /**
     * 拒绝访问时的响应消息
     *
     * @return 响应消息
     */
    String message() default "此接口仅供内部调用";

    /**
     * 拒绝访问时的响应状态码
     *
     * @return HTTP 状态码
     */
    int status() default 403;

    /**
     * 是否跳过 OAuth 鉴权
     * <p>
     * 默认跳过，内部接口通常不需要用户鉴权
     * </p>
     *
     * @return 是否跳过鉴权
     */
    boolean skipAuth() default true;

    /**
     * 接口描述
     *
     * @return 描述信息
     */
    String description() default "";
}
