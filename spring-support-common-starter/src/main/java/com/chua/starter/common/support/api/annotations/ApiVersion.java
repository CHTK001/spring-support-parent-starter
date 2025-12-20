package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API 版本控制注解
 * <p>
 * 用于实现 API 接口的版本管理，支持多版本 API 并行运行。
 * 支持语义化版本号（如 1, 1.0, 1.0.0, 1.0.0-rc.1, 1.0.0-release）。
 * </p>
 *
 * <h3>处理优先级：8（映射注册阶段最低）</h3>
 * <p>
 * 映射注册阶段优先级顺序：@ApiProfile(6) &gt; @ApiPlatform(7) &gt; @ApiVersion(8)
 * </p>
 * <p>
 * 处理阶段：映射注册阶段（应用启动时），为接口添加版本匹配条件。
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
 * <h3>版本指定方式</h3>
 * <ul>
 *   <li>查询参数：?apiVersion=1 或 ?apiVersion=v1</li>
 *   <li>请求头：X-Api-Version: 1</li>
 *   <li>最新版本：?apiVersion=latest 或 X-Api-Version: latest</li>
 * </ul>
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
 * // GET /api/user/info?apiVersion=1      -> 调用 getInfo()
 * // GET /api/user/info?apiVersion=2      -> 调用 getInfoV2()
 * // GET /api/user/info?apiVersion=latest -> 调用 getInfoV2() (最新版本)
 * // 或使用请求头 X-Api-Version: 2
 * </pre>
 *
 * @author CH
 * @since 2020-09-23
 * @version 1.1.0
 * @see com.chua.starter.common.support.api.control.ApiVersionCondition
 * @see com.chua.starter.common.support.api.control.ApiVersionRequestMappingHandlerMapping
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API版本号（语义化字符串）
     * <p>
     * 支持格式：1、1.0、1.0.0、1.0.0-rc.1、1.0.0-release 等。
     * </p>
     *
     * @return 版本号字符串，默认为 "1"
     */
    String value() default "1";
}
