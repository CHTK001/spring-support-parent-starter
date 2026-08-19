package com.chua.starter.common.support.jackson;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.core.KotlinDetector;
import org.springframework.util.ClassUtils;

/**
 * @since 2024/8/24
 * @author CH
 */
public class TypeResolverBuilder  extends ObjectMapper.DefaultTypeResolverBuilder {

    /** ForEverything */
    public static TypeResolverBuilder forEverything(ObjectMapper mapper) {
        return new TypeResolverBuilder(ObjectMapper.DefaultTyping.EVERYTHING, mapper.getPolymorphicTypeValidator());
    }

    /**
     * 创建 TypeResolverBuilder 实例
     * @param typing typing
     * @param PolymorphicTypeValidator PolymorphicTypeValidator
     */
    public TypeResolverBuilder(ObjectMapper.DefaultTyping typing, PolymorphicTypeValidator polymorphicTypeValidator) {
        super(typing, polymorphicTypeValidator);
    }

    @Override
    /** WithDefaultImpl */
    public ObjectMapper.DefaultTypeResolverBuilder withDefaultImpl(Class<?> defaultImpl) {
        return this;
    }

    /**
     * Method called to check if the default type handler should be used for given type. Note: "natural types" (String,
     * Boolean, Integer, Double) will never use typing; that is both due to them being concrete and final, and since
     * actual serializers and deserializers will also ignore any attempts to enforce typing.
     */
    public boolean useForType(JavaType javaType) {

        if (javaType.isJavaLangObject()) {
            return true;
        }

        javaType = resolveArrayOrWrapper(javaType);

        if (javaType.isEnumType() || ClassUtils.isPrimitiveOrWrapper(javaType.getRawClass())) {
            return false;
        }

        if (javaType.isFinal() && !KotlinDetector.isKotlinType(javaType.getRawClass())
                && javaType.getRawClass().getPackageName().startsWith("java")) {
            return false;
        }

        // [databind#88] Should not apply to JSON tree models:
        return !TreeNode.class.isAssignableFrom(javaType.getRawClass());
    }

    /** 解析ArrayOrWrapper */
    private JavaType resolveArrayOrWrapper(JavaType type) {

        while (type.isArrayType()) {
            type = type.getContentType();
            if (type.isReferenceType()) {
                type = resolveArrayOrWrapper(type);
            }
        }

        while (type.isReferenceType()) {
            type = type.getReferencedType();
            if (type.isArrayType()) {
                type = resolveArrayOrWrapper(type);
            }
        }

        return type;
    }
}

