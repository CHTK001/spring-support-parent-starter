package com.chua.starter.mybatis.annotations;

import java.lang.annotation.*;

/**
 * 删除操作仅限本表自身注解
 * 用于标记删除操作时只删除当前实体对应的数据，不进行关联删除
 *
 * @author CH
 * @since 2025/9/4 14:17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeleteOnlySelfOnMapper {
    /**
     * 数据库里代表“创建人”的列名
     */
    String createUserColumn() default "create_by";

    /**
     * Mapper 方法参数中，用来接收“当前登录人 ID”的参数名
     */
    String currentUserParam() default "sysUserId";
}
