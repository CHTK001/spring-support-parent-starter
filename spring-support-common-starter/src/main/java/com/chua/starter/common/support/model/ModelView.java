package com.chua.starter.common.support.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.MediaType;

/**
 * 模型视图
 *
 * @author CH
 */
@Data
@AllArgsConstructor
public class ModelView<T> {
    /**
     * 数据
     */
    private T data;
    /**
     * 类型
     */
    private MediaType mediaType;

    /**
     * 创建ModelView实例
     *
     * @param data 数据内容
     * @param <T>  数据类型
     * @return ModelView实例，默认使用JSON媒体类型
     */
    public static <T> ModelView<T> create(T data) {
        return new ModelView<>(data, MediaType.APPLICATION_JSON);
    }
}
