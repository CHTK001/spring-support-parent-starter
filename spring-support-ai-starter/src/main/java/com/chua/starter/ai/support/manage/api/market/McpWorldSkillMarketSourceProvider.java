package com.chua.starter.ai.support.manage.api.market;

import com.chua.common.support.spi.annotations.Spi;

/**
 * MCP World 市场源 SPI。
 *
 * @since 4.0.0.42
 */
@Spi("mcpworld")
public class McpWorldSkillMarketSourceProvider implements SkillMarketSourceProvider {

    @Override
    public String source() {
        return "mcpworld";
    }

    @Override
    public String displayName() {
        return "MCP World";
    }

    @Override
    public String url() {
        return "https://www.mcpworld.com/?category=most_popular";
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
