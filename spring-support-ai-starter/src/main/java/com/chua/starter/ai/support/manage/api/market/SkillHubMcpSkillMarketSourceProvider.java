package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * SkillHub MCP 市场源 SPI。
 *
 * @since 4.0.0.42
 */
@Spi("skillhub_mcp")
public class SkillHubMcpSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "skillhub_mcp";
    }

    @Override
    public String displayName() {
        return "SkillHub MCP";
    }

    @Override
    public String url() {
        return "https://skillhub.cn/mcp";
    }

    @Override
    public String marketType() {
        return "mcp";
    }
}
