package com.chua.starter.swagger.support.converter;

import io.swagger.v3.core.converter.*;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;

/**
 * 自定义转换器
 *
 * @author CH
 * @since 2025/5/27 13:04
 */
public class InheritedPropertyResolver implements ModelConverter {

    private void addParentProperties(Class<?> clazz, Schema schema) {
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            // 递归处理父类
            addParentProperties(superClass, schema);

            // 添加父类属性
            Arrays.stream(superClass.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .forEach(field -> {
                        Schema propertySchema = new Schema();
                        propertySchema.setName(field.getName());
                        propertySchema.setType(field.getType().getSimpleName().toLowerCase());
                        schema.addProperties(field.getName(), propertySchema);
                    });
        }
    }

    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(type);

        if (resolvedSchema.schema != null && type.getType() instanceof Class) {
            Class<?> clazz = (Class<?>) type.getType();
            // 获取父类属性
            addParentProperties(clazz, resolvedSchema.schema);
        }
        return resolvedSchema.schema;
    }
}
