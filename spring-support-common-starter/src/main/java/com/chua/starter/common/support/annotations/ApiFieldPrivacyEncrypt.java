package com.chua.starter.common.support.annotations;

import com.chua.starter.common.support.rule.PrivacySerializer;
import com.chua.starter.common.support.rule.PrivacyTypeEnum;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 自定义数据脱敏注解
 * @author Administrator
 */

@Target(ElementType.FIELD) // 作用在字段上
@Retention(RetentionPolicy.RUNTIME) // class文件中保留，运行时也保留，能通过反射读取到
@JacksonAnnotationsInside // 表示自定义自己的注解PrivacyEncrypt
@JsonSerialize(using = PrivacySerializer.class) // 该注解使用序列化的方式
public @interface ApiFieldPrivacyEncrypt {

    /**
     * 脱敏数据类型
     */
    PrivacyTypeEnum type() default PrivacyTypeEnum.NONE;

    /**
     * 前置不需要打码的长度
     */
    int prefixNoMaskLen() default 1;

    /**
     * 后置不需要打码的长度
     */
    int suffixNoMaskLen() default 1;

    /**
     * 用什么打码
     */
    String symbol() default "*";
}