package com.chua.starter.ai.support.spi;

import java.util.List;
import java.util.Map;

/**
* MCP 服务器离线 Provider SPI。
*
* <p>每个本地/内置 MCP 来源一个实现（如数据库内置 MCP 服务器），通过
* {@code @Spi("source-key")} 注册。开关状态持久化在 {@code sys_ai_global_setting}
* 的 {@code mcp} 分组。</p>
*
* @since 4.0.0.43
* @author CH
 */
public interface McpOfflineProvider {

    /**
    * 离线源名称。
    *
    * @return 源名称
     */
    String name();

    /**
    * 列出该来源提供的 MCP 服务器列表。
    *
    * @return MCP 服务器列表
     */
    List<Map<String, Object>> listMcpServers();
}
