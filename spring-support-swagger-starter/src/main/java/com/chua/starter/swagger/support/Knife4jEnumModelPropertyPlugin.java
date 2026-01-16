package com.chua.starter.swagger.support;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.PropertyCustomizer;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Knife4j枚举模型属性插件
 * 用于自定义枚举类型的属性显示
 *
 * @author CH
 * @since 2025/9/30 13:38
 */
public class Knife4jEnumModelPropertyPlugin implements PropertyCustomizer {

    /**
     * 定制属性
     *
     * @param property 属性Schema
     * @param type 注解类型
     * @return 定制后的属性Schema
     */
    @Override
    public Schema customize(Schema property, AnnotatedType type) {
        if (type.getType() instanceof Class clazz && clazz.isEnum() && SwaggerEnum.class.isAssignableFrom(clazz)) {
            SwaggerEnum[] enumValues = (SwaggerEnum[]) clazz.getEnumConstants();
            String enumDesc = Arrays.stream(enumValues)
                    .map(e -> getEnumDescription(e) + "<br />")
                    .collect(Collectors.joining(", "));
            property.setDescription(enumDesc);
            property.setEnum(Arrays.asList(enumValues));
        }
        return property;
    }

    /**
     * 获取枚举的描述
     *
     * @param e 枚举对象
     * @return 枚举的描述
     */
    private String getEnumDescription(SwaggerEnum e) {
        return e.getCode() +":" + e.getName();
    }
}