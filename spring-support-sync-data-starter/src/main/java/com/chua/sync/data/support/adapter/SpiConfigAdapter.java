package com.chua.sync.data.support.adapter;

import java.util.Map;

/**
 * SPI 配置适配器接口
 * 将前端配置转换为 SPI 实例
 *
 * @param <T> SPI 类型
 * @author CH
 * @since 2024/12/21
 */
public interface SpiConfigAdapter<T> {

    /**
     * 获取支持的 SPI 名称
     *
     * @return SPI 名称
     */
    String getSpiName();

    /**
     * 获取支持的 SPI 类型
     *
     * @return SPI 类型（INPUT, OUTPUT, DATA_CENTER, FILTER）
     */
    String getSpiType();

    /**
     * 根据配置创建 SPI 实例
     *
     * @param config 配置参数
     * @return SPI 实例
     */
    T create(Map<String, Object> config);

    /**
     * 测试配置是否有效
     *
     * @param config 配置参数
     * @return 测试结果描述，成功返回 null 或成功信息
     */
    String test(Map<String, Object> config);

    /**
     * 验证配置参数
     *
     * @param config 配置参数
     * @return 验证失败的错误信息，成功返回 null
     */
    default String validate(Map<String, Object> config) {
        return null;
    }

    /**
     * 关闭/清理资源
     *
     * @param instance SPI 实例
     */
    default void close(T instance) {
        // 默认不做处理
    }
}
