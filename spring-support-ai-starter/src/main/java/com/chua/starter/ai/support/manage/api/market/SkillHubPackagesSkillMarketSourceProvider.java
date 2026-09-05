package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * SkillHub 专家包市场源 SPI。
 *
 * @since 4.0.0.42
 */
@Spi("skillhub_packages")
public class SkillHubPackagesSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "skillhub_packages";
    }

    @Override
    public String displayName() {
        return "SkillHub Packages";
    }

    @Override
    public String url() {
        return "https://skillhub.cn/skillspackage";
    }

    @Override
    public String marketType() {
        return "preset";
    }
}
