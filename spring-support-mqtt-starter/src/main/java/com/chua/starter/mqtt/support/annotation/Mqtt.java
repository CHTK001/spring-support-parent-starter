package com.chua.starter.mqtt.support.annotation;

import java.lang.annotation.*;

/**
 * mqtt
 * @author CH
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Mqtt {

    String[] topic();

    int qos() default 0;

}
