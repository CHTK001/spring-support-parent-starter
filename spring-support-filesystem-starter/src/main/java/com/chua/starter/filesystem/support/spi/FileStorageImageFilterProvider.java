package com.chua.starter.filesystem.support.spi;

import com.chua.common.support.spi.annotations.Spi;

/**
* 文件存储图片滤镜 SPI 接口。
*
* <p>用于管理员动态配置图片滤镜（{@code FileStorageSetting#imageFilter}）时
* 枚举真实支持的滤镜实现：系统配置接口（{@code ?config=true}）会通过
* {@code ServiceProvider} 发现全部实现并下发展示名与 CSS 滤镜值，
* 前端据此渲染可选项并应用于图片缩略图与图片预览。</p>
*
* <p>实现类通过 {@code META-INF/extensions/} 注册，扩展方仅需新增实现并登记即可。</p>
*
* @author CH
* @since 4.0.0
 */
@Spi
public interface FileStorageImageFilterProvider {

    /**
    * 获取滤镜唯一名称。
    *
    * <p>写入 {@code FileStorageSetting#imageFilter} 持久化，需全局唯一。</p>
    *
    * @return 滤镜名称（如 {@code GRAYSCALE}）
     */
    String getName();

    /**
    * 获取滤镜展示名。
    *
    * @return 展示名（如 {@code 灰度}）
     */
    String getLabel();

    /**
    * 获取前端应用的 CSS filter 值。
    *
    * <p>返回空字符串表示无滤镜（原图）。</p>
    *
    * @return CSS filter 值（如 {@code grayscale(1)}）
     */
    String getCssFilter();
}
