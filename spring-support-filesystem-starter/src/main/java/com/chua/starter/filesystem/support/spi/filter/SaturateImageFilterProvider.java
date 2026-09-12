package com.chua.starter.filesystem.support.spi.filter;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider;

/**
* 鲜艳滤镜（高饱和）。
*
* <p>SPI 类型：{@code image-filter-saturate}。提升图片饱和度使色彩更鲜艳。</p>
*
* @author CH
* @since 4.0.0
 */
@Spi("image-filter-saturate")
public class SaturateImageFilterProvider implements FileStorageImageFilterProvider {

    /**
    * 获取滤镜唯一名称。
    *
    * @return 滤镜名称 SATURATE
     */
    @Override
    public String getName() {
        return "SATURATE";
    }

    /**
    * 获取滤镜展示名。
    *
    * @return 展示名 鲜艳
     */
    @Override
    public String getLabel() {
        return "鲜艳";
    }

    /**
    * 获取前端应用的 CSS filter 值。
    *
    * @return CSS filter 值 saturate(1.8)
     */
    @Override
    public String getCssFilter() {
        return "saturate(1.8)";
    }
}
