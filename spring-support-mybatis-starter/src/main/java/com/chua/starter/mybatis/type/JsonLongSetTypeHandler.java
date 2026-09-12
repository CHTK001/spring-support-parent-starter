package com.chua.starter.mybatis.type;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.chua.common.support.lang.json.Json;
import com.fasterxml.jackson.core.type.TypeReference;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * 参考 {@link com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} 实现
   * 在我们将字符串反序列化为 设置 并且泛型为 Long 时，如果每个元素的数值太小，会被处理成 Integer 类型，导致可能存在隐性的 BUG。
 *
   * 例如说哦，sys用户执行 的 post标识 属性
 *
 * @author 芋道源码
 * @author CH
 * @since 4.0.0
 */
public class JsonLongSetTypeHandler extends AbstractJsonTypeHandler<Object> {

    /** 类型_引用 */
    private static final TypeReference<Set<Long>> TYPE_REFERENCE = new TypeReference<Set<Long>>(){};

    /**
      * 创建 jsonlong设置类型处理器 实例
     * @param type 类型
     */
    public JsonLongSetTypeHandler(Class<?> type) {
        /**
         * 父。
         * @param type 类型
         */
        super(type);
    }

    /**
      * 创建 jsonlong设置类型处理器 实例
     * @param type 类型
     * @param field 字段
     * @param field 字段
     */
    public JsonLongSetTypeHandler(Class<?> type, Field field) {
        /**
         * 父。
         * @param type 类型
         * @param field 字段
         */
        super(type, field);
    }

    @Override
    /** 解析 */
    public Object parse(String json) {
        return Json.fromJson(json, TYPE_REFERENCE);
    }

    @Override
    /** 转为json */
    public String toJson(Object obj) {
        return Json.toJson(obj);
    }

}

