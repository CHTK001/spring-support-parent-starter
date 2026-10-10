package com.chua.starter.filesystem.support.spi.url;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.filesystem.support.spi.FileStorageUrlPreviewProvider;

/**
 * 后台三方 URL 预览（唯一支持 {@code ?url=} 的内置实现）。
 *
 * <p>SPI 类型：{@code url-preview-server}。由文件服务拉取远端内容，再走与本地对象
 * 完全相同的预览分发（{@code ?preview=true} / {@code ?format=pdf} / 客户端直渲染 /
 * SPI 预览页 / Range 全部复用）。</p>
 *
 * <p><b>为什么只有它支持</b>：外部地址的内容最终要由文件服务签发一次性票据、
 * 按桶配置做访问与转换判定，浏览器直连拿不到这些上下文。前端直连型实现可以
 * 枚举出来给集成方看，但不具备处理能力，选它仍会被服务端 403 拦下。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi("url-preview-server")
public class ServerUrlPreviewProvider implements FileStorageUrlPreviewProvider {

    /**
     * 获取实现唯一名称。
     *
     * @return 实现名 SERVER
     */
    @Override
    public String getName() {
        return "SERVER";
    }

    /**
     * 获取实现展示名。
     *
     * @return 展示名 后台
     */
    @Override
    public String getLabel() {
        return "后台";
    }

    /**
     * 获取实现说明。
     *
     * @return 说明文本
     */
    @Override
    public String getDescription() {
        return "由文件服务拉取外部地址内容，并在服务端完成预览分发";
    }

    /**
     * 是否支持 {@code ?url=}。
     *
     * @return 恒为 true
     */
    @Override
    public boolean supportsUrlPreview() {
        return true;
    }
}
