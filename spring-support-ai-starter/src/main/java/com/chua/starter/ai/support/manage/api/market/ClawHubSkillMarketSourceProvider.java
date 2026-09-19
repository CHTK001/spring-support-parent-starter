package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * clawhub 技能市场源 SPI。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("clawhub")
public class ClawHubSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "clawhub";
    }

    @Override
    public String displayName() {
        return "ClawHub";
    }

    @Override
    public String url() {
        return "https://clawhub.ai/";
    }

    @Override
    public String marketType() {
        return "skill";
    }
}
