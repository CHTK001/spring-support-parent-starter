package com.chua.starter.common.support.model;

import org.springframework.http.MediaType;

/**
 * 模型视图
 *
 * @author CH
 * @since 4.0.0
 */
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
     * 构造函数
     *
     * @param data 数据
     * @param mediaType 媒体类型
     */
    public ModelView(T data, MediaType mediaType) {
        this.data = data;
        this.mediaType = mediaType;
    }

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
    /**
     * 获取 数据
     *
     * @return data
     */
    public T getData() {
        return data;
    }

    /**
     * 设置 数据
     *
     * @param data 数据
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 获取 media类型
     *
     * @return mediaType
     */
    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * 设置 media类型
     *
     * @param mediaType media类型
     */
    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }


}
