package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * ClawHub 插件市场源 SPI。
 *
 * @since 4.0.0.42
 */
@Spi("clawhub_plugins")
public class ClawHubPluginsSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "clawhub_plugins";
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
}
