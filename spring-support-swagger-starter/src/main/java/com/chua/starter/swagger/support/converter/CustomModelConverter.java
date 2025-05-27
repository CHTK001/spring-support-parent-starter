package com.chua.starter.swagger.support.converter;

import com.chua.starter.swagger.support.annotation.HiddenParam;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;

/**
 * 自定义转换器
 *
 * @author CH
 * @since 2025/5/27 13:04
 */
public class CustomModelConverter implements ModelConverter {
    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context,
                          Iterator<ModelConverter> chain) {
        Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;

        if (schema == null) {
            return schema;
        }

        Type typeType = type.getType();
        if (typeType instanceof Class typeTypeClass) {
            processObjectType(schema, typeTypeClass);
        }

        if (typeType instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class rawTypeClass) {
                processObjectType(schema, rawTypeClass);
            }
        }

        return schema;
    }

    /**
     * 处理对象类型
     *
     * @param schema
     * @param javaType
     */
    private void processObjectType(Schema schema, Class<?> javaType) {
        // 获取Java类的实际字段
        for (Field field : javaType.getDeclaredFields()) {
            if (field.isAnnotationPresent(HiddenParam.class)) {
                schema.getProperties().remove(field.getName());
                continue;
            }

            Parameter parameter = field.getDeclaredAnnotation(Parameter.class);
            if (null != parameter && parameter.hidden()) {
                schema.getProperties().remove(field.getName());
                continue;
            }
        }
    }
}
