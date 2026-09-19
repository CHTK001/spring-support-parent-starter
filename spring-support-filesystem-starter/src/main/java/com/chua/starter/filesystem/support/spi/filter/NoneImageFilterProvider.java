package com.chua.starter.filesystem.support.spi.filter;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider;

/**
 * 原图滤镜（无滤镜效果）。
 *
 * <p>SPI 类型：{@code image-filter-none}。作为默认滤镜，
 * 对图片缩略图与图片预览不做任何处理。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Spi("image-filter-none")
public class NoneImageFilterProvider implements FileStorageImageFilterProvider {

    /**
     * 获取滤镜唯一名称。
     *
     * @return 滤镜名称 NONE
     */
    @Override
    public String getName() {
        return "NONE";
    }

    /**
     * 获取滤镜展示名。
     *
     * @return 展示名 原图
     */
    @Override
    public String getLabel() {
        return "原图";
    }

    /**
     * 获取前端应用的 CSS filter 值。
     *
     * @return 空字符串（无滤镜）
     */
    @Override
    public String getCssFilter() {
        return "";
    }
}
