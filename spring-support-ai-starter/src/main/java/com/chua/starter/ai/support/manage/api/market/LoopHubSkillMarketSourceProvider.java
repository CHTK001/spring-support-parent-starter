package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * LoopHub 技能市场源 SPI。
 *
 * @since 4.0.0.42
 */
@Spi("loophub")
public class LoopHubSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "loophub";
    }

    @Override
    public String displayName() {
        return "LoopHub";
    }

    @Override
    public String url() {
        return "https://hub.cocoloop.cn/popular";
    }

    @Override
    public String marketType() {
        return "skill";
    }
}
