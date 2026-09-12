package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
* skillhub MCP 市场源 SPI。
*
* @since 4.0.0.42
* @author CH
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

    @Override
    public boolean defaultEnabled() {
        // 暂无可用数据源，默认关闭，避免市场展示空态或伪造数据
        return false;
    }
}
