package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * API Version 版本
 * 支持多种版本格式:
 * 1. 数字格式: "1", "2"
 * 2. 小数格式: "1.0", "2.5"
 * 3. 加号后缀表示向后兼容: "1.0+"
 *
 * 版本比较规则:
 * - 1.0 等同于 1
 * - 当请求版本 >= API版本时匹配成功
 * - 带+号的版本表示向后兼容
 *
 * @author Administrator
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {
    /**
     * API版本号
     * 可使用整数(1)或小数(1.0)表示版本
     * 版本号大的接口优先匹配
     *
     * @return 版本号字符串
     */
    String version() default "0";
}
