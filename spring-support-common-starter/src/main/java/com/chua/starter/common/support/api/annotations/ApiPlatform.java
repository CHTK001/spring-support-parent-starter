package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API平台控制注解
 * <p>
 * 用于控制API接口在不同平台下的可用性，实现平台级别的接口隔离。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>多租户系统中不同租户启用不同的API</li>
 *   <li>不同部署环境（如：系统管理平台、监控平台）需要不同的API</li>
 *   <li>根据业务模块划分API的可见性</li>
 * </ul>
 *
 * <h3>配置要求</h3>
 * <pre>
 * plugin:
 *   api:
 *     platform:
 *       enable: true        # 必须开启平台控制
 *       name: SYSTEM        # 当前平台类型
 *       alias-name: custom  # 或使用自定义平台名称
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 类级别：整个控制器仅在 system 平台可用
 * @RestController
 * @RequestMapping("/api/admin")
 * @ApiPlatform("system")
 * public class AdminController { ... }
 *
 * // 方法级别：该接口在 system 和 monitor 平台都可用
 * @GetMapping("/stats")
 * @ApiPlatform({"system", "monitor"})
 * public Stats getStats() { ... }
 *
 * // 不标注@ApiPlatform 的接口在所有平台都可用
 * @GetMapping("/public")
 * public Info getPublicInfo() { ... }
 * </pre>
 *
 * <h3>平台类型枚举</h3>
 * <ul>
 *   <li>SYSTEM - 系统管理平台</li>
 *   <li>TENANT - 租户平台</li>
 *   <li>MONITOR - 监控平台</li>
 *   <li>SCHEDULER - 调度平台</li>
 *   <li>OAUTH - OAuth认证平台</li>
 * </ul>
 *
 * @author CH
 * @since 2020-09-23
 * @version 1.0.0
 * @see com.chua.starter.common.support.api.properties.ApiProperties.PlatformType
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiPlatform {

    /**
     * 平台名称列表
     * <p>
     * 支持配置多个平台名称，接口将在指定的所有平台中可用。
     * 平台名称不区分大小写。
     * </p>
     *
     * @return 平台名称数组
     */
    String[] value();
}

