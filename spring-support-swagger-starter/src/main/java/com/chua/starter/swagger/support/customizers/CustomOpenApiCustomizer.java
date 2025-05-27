package com.chua.starter.swagger.support.customizers;

import com.chua.starter.swagger.support.annotation.HiddenField;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 自定义
 *
 * @author CH
 * @since 2025/5/27 9:30
 */
public class CustomOpenApiCustomizer implements OpenApiCustomizer {
    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        for (Map.Entry<String, Schema> entry : openApi.getComponents().getSchemas().entrySet()) {
            String schemaName = entry.getKey();
            Schema schema = entry.getValue();

            try {
                Class<?> modelClass = Class.forName("com.yourpackage.model." + schemaName);
                processHiddenFields(modelClass, schema);
            } catch (ClassNotFoundException e) {
                // 忽略无法找到的类
            }
        }
    }

    private void processHiddenFields(Class<?> clazz, Schema schema) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(HiddenField.class)) {
                schema.getProperties().remove(field.getName());
            }

            Parameter parameter = field.getDeclaredAnnotation(Parameter.class);
            if (parameter != null && parameter.hidden()) {
                schema.getProperties().remove(field.getName());
            }
        }
    }
}
