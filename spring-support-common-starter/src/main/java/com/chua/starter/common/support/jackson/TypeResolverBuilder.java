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

    /**
     * foreverything
     *
     * @param mapper 映射器
     * @return forEverything的结果
     */
    public static TypeResolverBuilder forEverything(ObjectMapper mapper) {
        /**
         * 类型解析器构建器。
         * @param ObjectMapper.DefaultTyping.EVERYTHING 对象映射器.defaulttyping.everything
         * @param mapper.getPolymorphicTypeValidator( 映射器.getpolymorphic类型validator(
         */
        return new TypeResolverBuilder(ObjectMapper.DefaultTyping.EVERYTHING, mapper.getPolymorphicTypeValidator());
    }

    /**
      * 创建 类型解析器构建器 实例
     * @param typing typing
     * @param polymorphicTypeValidator polymorphic类型validator
     * @param polymorphicTypeValidator polymorphic类型validator
     */
    public TypeResolverBuilder(ObjectMapper.DefaultTyping typing, PolymorphicTypeValidator polymorphicTypeValidator) {
        /**
         * 父。
         * @param typing 类型
         * @param polymorphicTypeValidator polymorphic类型validator
         */
        super(typing, polymorphicTypeValidator);
    }

    @Override
    /** with默认impl */
    public ObjectMapper.DefaultTypeResolverBuilder withDefaultImpl(Class<?> defaultImpl) {
        return this;
    }

    /**
      * 方法 called 转为 检查 if the 默认 类型 处理器 should be used for given 类型. 笔记: "natural 类型" (字符串,
       * 布尔值, Integer, Double) will 从不 use 类型; that 是否 both due 转为 them 存在 混凝土 和 最终, 和 自
       * actual 序列化器 和 反序列化器 will also ignore 任意 尝试 转为 enforce 类型.
     * @param javaType Java类型
     * @return usefor类型的结果
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

 // [databind#88] Should not apply 转为 JSON 树 模型:
        return !TreeNode.class.isAssignableFrom(javaType.getRawClass());
    }

    /**
     * 解析arrayor包装器
     *
     * @param type 类型
     * @return resolvearrayor包装器的结果
     */
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

