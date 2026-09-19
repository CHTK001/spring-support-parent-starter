package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.spi.PluginMarketSourceProvider;

/**
 * Open VSX 插件市场源（在线）。
 *
 * <p>源标识与 {@code utils-support-datasearch-starter} 的
 * {@code OpenVsxPluginOnlineProvider#name()} 保持一致，插件清单服务据此委派真实搜索请求。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("open-vsx")
public class OpenVsxPluginMarketSourceProvider implements PluginMarketSourceProvider {

    @Override
    public String source() {
        return "open-vsx";
    }

    @Override
    public String displayName() {
        return "Open VSX";
    }

    @Override
    public String url() {
        return "https://open-vsx.org";
    }

    @Override
    public String marketType() {
        return "plugin";
    }
}
