package com.chua.starter.filesystem.support.spi.filter;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider;

/**
 * 怀旧滤镜。
 *
 * <p>SPI 类型：{@code image-filter-sepia}。将图片转为棕褐色调的复古效果。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Spi("image-filter-sepia")
public class SepiaImageFilterProvider implements FileStorageImageFilterProvider {

    /**
     * 获取滤镜唯一名称。
     *
     * @return 滤镜名称 SEPIA
     */
    @Override
    public String getName() {
        return "SEPIA";
    }

    /**
     * 获取滤镜展示名。
     *
     * @return 展示名 怀旧
     */
    @Override
    public String getLabel() {
        return "怀旧";
    }

    /**
     * 获取前端应用的 CSS filter 值。
     *
     * @return CSS filter 值 sepia(0.9)
     */
    @Override
    public String getCssFilter() {
        return "sepia(0.9)";
    }
}
