package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * skillhub 技能市场源 SPI。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("skillhub")
public class SkillHubSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "skillhub";
    }

    @Override
    public String displayName() {
        return "SkillHub";
    }

    @Override
    public String url() {
        return "https://skillhub.cn/skills?sortBy=score";
    }

    @Override
    public String marketType() {
        return "skill";
    }
}
