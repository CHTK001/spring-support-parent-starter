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
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ModelView<T> create(T data) {
        return new ModelView<>(data, MediaType.APPLICATION_JSON);
    }
}
