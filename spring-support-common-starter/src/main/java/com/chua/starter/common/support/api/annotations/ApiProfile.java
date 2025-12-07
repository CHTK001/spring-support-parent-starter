package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API环境配置注解
 * <p>
 * 用于控制API接口在不同Spring Profile环境下的可用性�?
 * </p>
 *
 * <h3>使用场景�?/h3>
 * <ul>
 *   <li>某些调试接口仅在开发环境可�?/li>
 *   <li>测试专用接口仅在测试环境暴露</li>
 *   <li>生产环境禁用某些敏感接口</li>
 * </ul>
 *
 * <h3>配置要求�?/h3>
 * <pre>
 * # application.yml
 * spring:
 *   profiles:
 *     active: dev  # 当前激活的环境
 *
 * plugin:
 *   api:
 *     platform:
 *       enable: true  # 需要开启平台控�?
 * </pre>
 *
 * <h3>使用示例�?/h3>
 * <pre>
 * // 仅在开发环境可�?
 * &#64;GetMapping("/debug")
 * &#64;ApiProfile("dev")
 * public Debug getDebugInfo() { ... }
 *
 * // 在开发和测试环境可用
 * &#64;GetMapping("/test-data")
 * &#64;ApiProfile({"dev", "test"})
 * public TestData getTestData() { ... }
 *
 * // 类级别：整个控制器仅在指定环境可�?
 * &#64;RestController
 * &#64;ApiProfile("dev")
 * public class DevToolsController { ... }
 * </pre>
 *
 * @author CH
 * @since 2020-09-23
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiProfile {

    /**
     * 环境名称列表
     * <p>
     * 支持配置多个环境名称，如 dev、test、prod 等�?
     * 接口将在指定的所有环境中可用�?
     * </p>
     *
     * @return 环境名称数组
     */
    String[] value();
}

