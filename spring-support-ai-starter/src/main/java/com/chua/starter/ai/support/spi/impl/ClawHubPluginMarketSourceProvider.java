package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.spi.PluginMarketSourceProvider;

/**
 * ClawHub 插件市场源（在线）。
 *
 * <p>该市场当前没有公开搜索接口，底层来源只支持打开主页，因此默认关闭，
 * 避免前端展示恒为空的市场条目。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("clawhub-plugins")
public class ClawHubPluginMarketSourceProvider implements PluginMarketSourceProvider {

    @Override
    public String source() {
        return "clawhub-plugins";
    }

    @Override
    public String displayName() {
        return "ClawHub Plugins";
    }

    @Override
    public String url() {
        return "https://clawhub.ai/plugins";
    }

    @Override
    public String marketType() {
        return "plugin";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }
}
