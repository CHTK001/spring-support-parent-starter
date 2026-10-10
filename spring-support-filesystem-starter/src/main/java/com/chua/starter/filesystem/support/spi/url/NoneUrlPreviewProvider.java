package com.chua.starter.filesystem.support.spi.url;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageUrlPreviewProvider;

/**
 * 关闭三方 URL 预览（默认）。
 *
 * <p>SPI 类型：{@code url-preview-none}。作为配置默认值，表示不使用外部地址预览，
 * 此时 {@code ?url=} 一律 403。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi("url-preview-none")
public class NoneUrlPreviewProvider implements FileStorageUrlPreviewProvider {

    /**
     * 获取实现唯一名称。
     *
     * @return 实现名 NONE
     */
    @Override
    public String getName() {
        return "NONE";
    }

    /**
     * 获取实现展示名。
     *
     * @return 展示名 关闭
     */
    @Override
    public String getLabel() {
        return "关闭";
    }

    /**
     * 获取实现说明。
     *
     * @return 说明文本
     */
    @Override
    public String getDescription() {
        return "不处理外部地址：?url= 一律返回 403";
    }

    /**
     * 是否支持 {@code ?url=}。
     *
     * @return 恒为 false
     */
    @Override
    public boolean supportsUrlPreview() {
        return false;
    }
}
