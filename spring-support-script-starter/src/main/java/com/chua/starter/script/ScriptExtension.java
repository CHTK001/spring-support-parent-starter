package com.chua.starter.script;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 脚本扩展
 *
 * @author CH
 */
@Retention(RetentionPolicy.RUNTIME)

@Target(ElementType.TYPE)
public @interface ScriptExtension {


    String value();
}
