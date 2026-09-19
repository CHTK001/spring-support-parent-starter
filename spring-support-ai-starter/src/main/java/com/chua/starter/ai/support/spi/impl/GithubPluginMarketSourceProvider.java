package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.spi.PluginMarketSourceProvider;

/**
 * GitHub 插件仓库市场源（在线）。
 *
 * <p>源标识与 {@code utils-support-datasearch-starter} 的
 * {@code GitHubPluginOnlineProvider#name()} 保持一致，插件清单服务据此委派真实代码搜索请求。
 * 未配置 GitHub 令牌时受公共接口速率限制。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("github-plugins")
public class GithubPluginMarketSourceProvider implements PluginMarketSourceProvider {

    @Override
    public String source() {
        return "github-plugins";
    }

    @Override
    public String displayName() {
        return "GitHub 插件仓库";
    }

    @Override
    public String url() {
        return "https://github.com";
    }

    @Override
    public String marketType() {
        return "plugin";
    }
}
