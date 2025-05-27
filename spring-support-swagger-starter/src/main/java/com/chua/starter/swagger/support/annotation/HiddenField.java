package com.chua.starter.swagger.support.annotation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 隐藏字段
 *
 * @author CH
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Schema(hidden = true)  // 继承标准的hidden行为
public @interface HiddenField {
}