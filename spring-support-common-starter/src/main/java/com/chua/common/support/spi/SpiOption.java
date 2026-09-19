package com.chua.common.support.spi;

/**
 * SPI 选项接口，定义 SPI 扩展点的元信息契约
 *
 * @author CH
 * @since 4.0.0
 */
public interface SpiOption {
    /**
     * 获取 SPI 扩展的名称
     *
     * @return 扩展名称
     */
    String name();

    /**
     * 获取该 SPI 扩展支持的类型列表
     *
     * @return 支持的类型数组，默认返回空数组
     */
    default String[] supportedTypes() {
        return new String[0];
    }
}
