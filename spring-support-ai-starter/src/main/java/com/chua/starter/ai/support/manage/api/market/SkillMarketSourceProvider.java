package com.chua.starter.ai.support.manage.api.market;

/**
* 技能市场源 SPI 提供者。
*
* <p>每个市场源一个实现（如 ClawHub、LoopHub、SkillHub、SkillHub MCP、MCP World、
* clawhub Plugins、skillhub 包），通过 {@code @Spi("source-key")} 注册。
* 前端「技能 / MCP 市场」设置页枚举全部 SPI 渲染来源配置列表（每个源一个开关），
* 仅渲染启用来源的市场内容。默认启用，开关状态持久化在 {@code sys_ai_global_setting}
* 的 {@code skills} 分组（字段 {@code market_<source>_enabled}）。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface SkillMarketSourceProvider {

    /**
    * 市场源标识（与前端 {@code SkillMarketSource} 枚举一致）。
    *
    * @return 源标识，如 clawhub / skillhub / mcpworld
    */
    String source();

    /**
    * 市场源显示名称。
    *
    * @return 显示名称
    */
    String displayName();

    /**
    * 市场源主页地址（用于「在浏览器中打开市场」）。
    *
    * @return 外部 URL
    */
    String url();

    /**
    * 市场类型：skill=技能市场，mcp=MCP 市场，plugin=插件市场，preset=专家包市场。
    *
    * @return 市场类型
    */
    String marketType();

    /**
    * 默认是否启用（未显式配置开关时的兜底值）。
    *
    * @return true 表示默认启用
    */
    default boolean defaultEnabled() {
        return true;
    }
}
