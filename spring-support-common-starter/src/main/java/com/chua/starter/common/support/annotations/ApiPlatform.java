package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 平台名称注解
 * <p>用于标识接口或方法所属的平台</p>
 * <p>设置了只允许设置的平台才有整个接口</p>
 * @see com.chua.starter.common.support.properties.ControlProperties.Platform
 * @author CH
 * @since 2023-01-01
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiPlatform {
    /**
     * 平台名称
     * <p>支持配置多个平台名称，如：{"platform1", "platform2"}</p>
     * <p>示例：@ApiPlatform({"web", "mobile"})</p>
     * @see com.chua.starter.common.support.properties.ControlProperties.Platform
     * @return 平台名称数组
     */
    String[] value() default {};

}
