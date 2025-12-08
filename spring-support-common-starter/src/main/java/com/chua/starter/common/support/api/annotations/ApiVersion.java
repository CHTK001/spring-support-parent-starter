package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API 版本控制注解
 * <p>
 * 用于实现 API 接口的版本管理，支持多版本 API 并行运行。
 * </p>
 *
 * <h3>处理优先级：7（最低）</h3>
 * <p>
 * 在映射注册阶段处理，启动时为接口添加版本号路径前缀。
 * 处理阶段：映射注册阶段（应用启动时）。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>API接口升级时需要保持向后兼容</li>
 *   <li>不同客户端需要调用不同版本的API</li>
 *   <li>渐进式迁移旧版本API到新版本</li>
 * </ul>
 *
 * <h3>配置要求</h3>
 * <pre>
 * plugin:
 *   api:
 *     version:
 *       enable: true  # 必须开启版本控制
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 类级别：所有方法默认使用v1版本
 * &#64;RestController
 * &#64;RequestMapping("/api/user")
 * &#64;ApiVersion(1)
 * public class UserController {
 *
 *     // 继承类级别版本 v1
 *     &#64;GetMapping("/info")
 *     public User getInfo() { ... }
 *
 *     // 方法级别覆盖：使用v2版本
 *     &#64;GetMapping("/info")
 *     &#64;ApiVersion(2)
 *     public UserV2 getInfoV2() { ... }
 * }
 *
 * // 请求方式：
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
     * API版本号
     * <p>
     * 可使用整数(1)或小数(1.0)表示版本。
     * </p>
     *
     * @return 版本号，默认为 1
     */
    double value() default 1;
}
