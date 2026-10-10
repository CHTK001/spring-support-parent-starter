package com.chua.starter.filesystem.support.spi.filter;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider;

/**
 * 灰度滤镜。
 *
 * <p>SPI 类型：{@code image-filter-grayscale}。将图片去色显示为黑白效果。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Spi("image-filter-grayscale")
public class GrayscaleImageFilterProvider implements FileStorageImageFilterProvider {

    /**
     * 获取滤镜唯一名称。
     *
     * @return 滤镜名称 GRAYSCALE
     */
    @Override
    public String getName() {
        return "GRAYSCALE";
    }

    /**
     * 获取滤镜展示名。
     *
     * @return 展示名 灰度
     */
    @Override
    public String getLabel() {
        return "灰度";
    }

    /**
     * 获取前端应用的 CSS filter 值。
     *
     * @return CSS filter 值 grayscale(1)
     */
    @Override
    public String getCssFilter() {
        return "grayscale(1)";
    }

    /**
     * 获取图片路径参数。
     *
     * @return {@code grayscale=1}
     */
    @Override
    public String getPathParam() {
        return "grayscale=1";
    }

    /**
     * 是否由后台图片管线执行。
     *
     * @return true（JDK 图片管线支持灰度）
     */
    @Override
    public boolean isServerSide() {
        return true;
    }
}
