package com.chua.starter.ai.support.spi;

/**
* MCP 市场源 SPI（在线市场）。
*
* <p>每个在线 MCP 市场源一个实现，通过 {@code @Spi("source-key")} 注册。
* 开关状态持久化在 {@code sys_ai_global_setting} 的 {@code mcp} 分组。</p>
*
* @since 4.0.0.43
* @author CH
 */
public interface McpMarketSourceProvider {

    /**
    * 市场源标识。
    *
    * @return 源标识
     */
    String source();

    /**
    * 市场源显示名称。
    *
    * @return 显示名称
     */
    String displayName();

    /**
    * 市场源主页地址。
    *
    * @return 外部 URL
     */
    String url();

    /**
    * 市场类型：mcp。
    *
    * @return 市场类型
     */
    String marketType();

    /**
    * 默认是否启用。
    *
    * @return true 表示默认启用
     */
    default boolean defaultEnabled() {
        return true;
    }
}
