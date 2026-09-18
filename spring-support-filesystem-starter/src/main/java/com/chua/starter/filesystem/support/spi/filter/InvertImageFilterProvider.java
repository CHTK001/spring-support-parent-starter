package com.chua.starter.filesystem.support.spi.filter;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider;

/**
* 反色滤镜。
*
* <p>SPI 类型：{@code image-filter-invert}。将图片颜色取反（负片效果）。</p>
*
* @author CH
* @since 4.0.0
 */
@Spi("image-filter-invert")
public class InvertImageFilterProvider implements FileStorageImageFilterProvider {

    /**
    * 获取滤镜唯一名称。
    *
    * @return 滤镜名称 INVERT
    */
    @Override
    public String getName() {
        return "INVERT";
    }

    /**
    * 获取滤镜展示名。
    *
    * @return 展示名 反色
    */
    @Override
    public String getLabel() {
        return "反色";
    }

    /**
    * 获取前端应用的 CSS filter 值。
    *
    * @return CSS filter 值 invert(1)
    */
    @Override
    public String getCssFilter() {
        return "invert(1)";
    }
}
