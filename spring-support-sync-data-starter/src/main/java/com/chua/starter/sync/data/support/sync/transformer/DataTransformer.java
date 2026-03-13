package com.chua.starter.sync.data.support.sync.transformer;

import java.util.Map;

/**
 * 数据转换器接口
 *
 * @author System
 * @since 2026/03/09
 */
public interface DataTransformer {

    /**
     * 转换数据
     *
     * @param input 输入数据
     * @param config 转换配置
     * @return 转换后的数据
     */
    Map<String, Object> transform(Map<String, Object> input, TransformConfig config);

    /**
     * 验证配置
     *
     * @param config 转换配置
     * @return 配置是否有效
     */
    boolean validateConfig(TransformConfig config);
}
