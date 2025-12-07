package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API版本控制注解
 * <p>
 * 用于实现API接口的版本管理，支持多版本API并行运行�?
 * </p>
 *
 * <h3>使用场景�?/h3>
 * <ul>
 *   <li>API接口升级时需要保持向后兼�?/li>
 *   <li>不同客户端需要调用不同版本的API</li>
 *   <li>渐进式迁移旧版本API到新版本</li>
 * </ul>
 *
 * <h3>配置要求�?/h3>
 * <pre>
 * plugin:
 *   api:
 *     version:
 *       enable: true  # 必须开启版本控�?
 * </pre>
 *
 * <h3>使用示例�?/h3>
 * <pre>
 * // 类级别：所有方法默认使用v1版本
 * &#64;RestController
 * &#64;RequestMapping("/api/user")
 * &#64;ApiVersion(1)
 * public class UserController {
 *
 *     // 继承类级别版�?v1
 *     &#64;GetMapping("/info")
 *     public User getInfo() { ... }
 *
 *     // 方法级别覆盖：使用v2版本
 *     &#64;GetMapping("/info")
 *     &#64;ApiVersion(2)
 *     public UserV2 getInfoV2() { ... }
 * }
 *
 * // 请求方式�?
 * // GET /api/v1/user/info  -> 调用 getInfo()
 * // GET /api/v2/user/info  -> 调用 getInfoV2()
 * </pre>
 *
 * @author CH
 * @since 2020-09-23
 * @version 1.0.0
 * @see com.chua.starter.common.support.api.control.ApiVersionCondition
 * @see com.chua.starter.common.support.api.control.ApiVersionRequestMappingHandlerMapping
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API版本�?
     * <p>
     * 可使用整�?1)或小�?1.0)表示版本�?
     * </p>
     *
     * @return 版本号，默认�?
     */
    double value() default 1;
}

