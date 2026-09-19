package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.spi.PluginMarketSourceProvider;

/**
 * VS Code Marketplace 插件市场源（在线）。
 *
 * <p>源标识与 {@code utils-support-datasearch-starter} 的
 * {@code VscodeMarketplacePluginOnlineProvider#name()} 保持一致，插件清单服务据此委派真实搜索请求。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("vscode-marketplace")
public class VscodeMarketplacePluginMarketSourceProvider implements PluginMarketSourceProvider {

    @Override
    public String source() {
        return "vscode-marketplace";
    }

    @Override
    public String displayName() {
        return "VS Code Marketplace";
    }

    @Override
    public String url() {
        return "https://marketplace.visualstudio.com";
    }

    @Override
    public String marketType() {
        return "plugin";
    }
}
