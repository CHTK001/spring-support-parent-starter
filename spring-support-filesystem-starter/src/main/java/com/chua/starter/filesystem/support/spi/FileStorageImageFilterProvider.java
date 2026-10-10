package com.chua.starter.filesystem.support.spi;

import com.chua.common.support.spi.annotations.Spi;

/**
 * 文件存储图片滤镜 SPI 接口。
 *
 * <p>用于管理员动态配置图片滤镜（{@code FileStorageSetting#imageFilter}）时
 * 枚举真实支持的滤镜实现：系统配置接口（{@code ?config=true}）会通过
 * {@code ServiceProvider} 发现全部实现并下发展示名、CSS 滤镜值与<b>图片路径参数</b>，
 * 前端据此渲染可选项；选中某滤镜后，图片缩略图与图片预览的地址都会拼上该滤镜的
 * 路径参数，使图片渲染先经过滤镜。</p>
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

    /**
     * 获取该滤镜的<b>图片路径参数</b>（拼在图片地址查询串上的处理指令）。
     *
     * <p>前端渲染图片（缩略图、图片预览）时把本值拼进地址，服务端据此在返回字节
     * 前先按该滤镜处理一遍——即「选中滤镜后图片的渲染先走滤镜」。
     * 返回值形如 {@code grayscale=1}、{@code blur=3}，多个参数用 {@code &} 连接。</p>
     *
     * <p>返回空串表示<b>不产生服务端处理参数</b>：原图滤镜本就不处理；
     * 后端图片管线不支持的滤镜（反色/怀旧/鲜艳）也返回空串，此时由前端按
     * {@link #getCssFilter()} 兜底展示，服务端不会收到无法执行的处理参数。</p>
     *
     * @return 路径参数；原图或后端不支持时为空串
     */
    default String getPathParam() {
        return "";
    }

    /**
     * 该滤镜是否由<b>后台图片管线</b>真正执行。
     *
     * <p>true 时前端把 {@link #getPathParam()} 拼进图片地址即可得到已处理的字节，
     * 不再叠加 CSS 滤镜（否则灰度再压一层灰度、模糊叠两次模糊，效果失真）；
     * false 时前端回退到 {@link #getCssFilter()} 的 CSS 预览。</p>
     *
     * @return true 表示后台会执行该滤镜
     */
    default boolean isServerSide() {
        return false;
    }
}
