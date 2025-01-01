package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.rule.ApiIgnoreSerializer;
import com.chua.starter.common.support.rule.PrivacySerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * API 忽略返回字段
 * @author CH
 * @since 2025/1/1
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JsonSerialize(using = ApiIgnoreSerializer.class)
public @interface ApiIgnore {

    /**
     * 忽略分组
     * @return 忽略分组
     */
    Class<?>[] value();
}
