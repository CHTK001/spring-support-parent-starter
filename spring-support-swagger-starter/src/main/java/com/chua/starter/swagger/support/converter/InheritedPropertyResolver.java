package com.chua.starter.swagger.support.converter;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;

/**
 * 继承属性解析器
 * 用于解析父类属性并添加到Schema中
 *
 * @author CH
 * @since 2025/5/27 13:04
 */
public class InheritedPropertyResolver implements ModelConverter {

    /**
     * 添加父类属性
     *
     * @param clazz 类
     * @param schema Schema对象
     */
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

    /**
     * 解析Schema
     *
     * @param type 注解类型
     * @param context 模型转换器上下文
     * @param chain 转换器链
     * @return 解析后的Schema
     */
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
