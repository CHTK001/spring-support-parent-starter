package com.chua.starter.common.support.result;

import com.chua.common.support.utils.ClassUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 結果
 *
 * @author CH
 */
@Data
@Accessors(chain = true)
public class ReturnResultBuilder<T> {
    protected ReturnResultBuilder() {
    }

    /**
     * 编码
     */
    private int code;
    /**
     * 数据
     */
    private T data;
    /**
     * 信息
     */
    private String msg;
    /**
     * 模板
     */
    private transient volatile Map<String, Object> temp = new HashMap<>();


    /**
     * 编码
     *
     * @param code 编码
     */
    public ReturnResultBuilder<T> code(int code) {
        this.code = code;
        return this;
    }

    /**
     * 信息
     *
     * @param msg 信息
     */
    public ReturnResultBuilder<T> msg(String msg) {
        this.msg = msg;
        return this;
    }

    /**
     * 数据
     *
     * @param data 数据
     */
    public ReturnResultBuilder<T> data(T data) {
        this.data = data;
        return this;
    }

    /**
     * 构建消息
     *
     * @return 消息
     */
    public ReturnResult<T> build() {
        return build(null);
    }

    /**
     * 构建消息
     *
     * @param target 类型
     * @return 消息
     */
    public ReturnResult<T> build(Class<T> target) {
        ReturnResult<T> rs = ReturnResult.of();
        rs.setCode(code);
        rs.setMsg(msg);
        rs.setData(data);
        if (null == data && !temp.isEmpty() && null != target) {
            rs.setData(analysis(target));

        }
        return rs;
    }

    /**
     * 分析实体
     *
     * @param target 类型
     * @return 实体
     */
    private T analysis(Class<T> target) {
        T realType = realType(target);
        if (null == realType) {
            return null;
        }

        Class<?> aClass = realType.getClass();
        for (Map.Entry<String, Object> entry : temp.entrySet()) {
            String entryKey = entry.getKey();
            Object value = entry.getValue();

            if (null == value) {
                continue;
            }

            render(aClass, realType, entryKey, value);
        }

        return realType;

    }

    /**
     * 渲染
     *
     * @param aClass   类型
     * @param realType 实体
     * @param entryKey key
     * @param value    value
     */
    private void render(Class<?> aClass, T realType, String entryKey, Object value) {
        Field field = ClassUtils.findField(aClass, entryKey);
        if (null == field) {
            return;
        }
        ClassUtils.setAccessible(field);
        try {
            ClassUtils.setFieldValue(field, value, realType);
        } catch (Exception ignored) {
        }
    }

    /**
     * 获取真实实体
     *
     * @param target 类型
     * @return 实体
     */
    private T realType(Class<T> target) {
        Class<?> aClass = this.getClass();
        Type superclass = aClass.getGenericSuperclass();
        if(superclass == Object.class) {
            try {
                return target.newInstance();
            } catch (Exception ignored) {
                return null;
            }
        }
        Type type = null;
        if (superclass instanceof ParameterizedType) {
            type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        }

        if (!(type instanceof Class<?>)) {
            return null;
        }

        try {
            return (T) ((Class<?>) type).newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建结果
     *
     * @param <T> 类型
     * @return 构造器
     */
    public static <T> ReturnResultBuilder<T> newBuilder() {
        return new ReturnResultBuilder<>();
    }

    /**
     * 返回值
     *
     * @param field 字段
     * @param value 数据
     * @return this
     */
    public ReturnResultBuilder<T> with(String field, Object value) {
        temp.put(field, value);
        return this;
    }

}
