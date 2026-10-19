package com.chua.starter.common.support.model;

import org.springframework.http.MediaType;

/**
 * 模型视图
 *
 * @param <T>   数据类型
 * @param data     数据
 * @param mediaType 类型
 * @author CH
 * @since 4.0.0
 */
public record ModelView<T>(T data, MediaType mediaType) {

    /**
     * 创建模型view实例
     *
     * @param data 数据内容
     * @param <T>  数据类型
     * @return ModelView实例，默认使用JSON媒体类型
     */
    public static <T> ModelView<T> create(T data) {
        return new ModelView<>(data, MediaType.APPLICATION_JSON);
    }
}
