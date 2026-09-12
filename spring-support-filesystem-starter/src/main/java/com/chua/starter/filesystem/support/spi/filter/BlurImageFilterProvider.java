package com.chua.starter.filesystem.support.spi.filter;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider;

/**
* 模糊滤镜。
*
* <p>SPI 类型：{@code image-filter-blur}。对图片进行高斯模糊处理，
* 可用于隐藏敏感内容的粗略预览场景。</p>
*
* @author CH
* @since 4.0.0
 */
@Spi("image-filter-blur")
public class BlurImageFilterProvider implements FileStorageImageFilterProvider {

    /**
    * 获取滤镜唯一名称。
    *
    * @return 滤镜名称 BLUR
     */
    @Override
    public String getName() {
        return "BLUR";
    }

    /**
    * 获取滤镜展示名。
    *
    * @return 展示名 模糊
     */
    @Override
    public String getLabel() {
        return "模糊";
    }

    /**
    * 获取前端应用的 CSS filter 值。
    *
    * @return CSS filter 值 blur(3px)
     */
    @Override
    public String getCssFilter() {
        return "blur(3px)";
    }
}
