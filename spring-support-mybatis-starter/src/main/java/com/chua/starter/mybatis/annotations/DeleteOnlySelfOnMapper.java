package com.chua.starter.mybatis.annotations;

import java.lang.annotation.*;

/**
* 删除操作仅限本表自身注解
* 用于标记删除操作时只删除当前实体对应的数据，不进行关联删除
*
* @since 2025/9/4 14:17
* @author CH
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeleteOnlySelfOnMapper {
    /**
    * 数据库里代表“创建人”的列名
    * @return 创建用户column的结果
    */
    String createUserColumn() default "create_by";

    /**
    * 映射器 方法参数中，用来接收“当前登录人 标识”的参数名
    * @return 当前用户参数的结果
    */
    String currentUserParam() default "sysUserId";
}
