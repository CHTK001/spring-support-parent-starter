package com.chua.starter.swagger.support.resolver;

import com.chua.starter.swagger.support.annotation.HiddenParam;
import com.chua.starter.swagger.support.customize.CustomOperationCustomizer1;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Type;

/**
 * 模型解释器
 *
 * @author CH
 * @since 2025/7/7 11:03
 */
public class CustomModelResolver extends ModelResolver {
    static Class USER_VALUE = null;

    static {
        try {
            USER_VALUE = ClassUtils.forName("com.chua.starter.oauth.client.support.annotation.UserValue", CustomOperationCustomizer1.class.getClassLoader());
        } catch (Exception ignored) {
        }
    }

    public CustomModelResolver(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected boolean shouldIgnoreClass(Type type) {
        if (type instanceof Class) {
            return ((Class<?>) type).isAnnotationPresent(HiddenParam.class)
                    || (null != USER_VALUE && ((Class<?>) type).isAnnotationPresent(USER_VALUE));
        }
        return super.shouldIgnoreClass(type);
    }
}
