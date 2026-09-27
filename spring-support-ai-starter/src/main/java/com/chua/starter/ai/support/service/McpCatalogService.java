package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;

/**
 * MCP 来源清单服务。
 *
 * <p>与技能目录（SkillCatalogService）同构：聚合「内置推荐（可一键安装）」与
 * 「在线检索（第三方市场/仓库引用）」两类来源，来源开关持久化于全局设置
 * {@code mcp} 分组。</p>
 *
 * <p>内置推荐自带可直接连接的 transport（远程 streamable HTTP 免鉴权，或本机
 * npx 启动的官方 stdio 服务器），安装即落库；在线检索仅返回真实的第三方仓库
 * 引用（源侧不提供可直连 transport），引导管理员手动配置传输，不伪造数据。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public interface McpCatalogService {

    /**
     * 获取全部来源配置与开关。
     *
     * @return 分组配置
     */
    Map<String, Object> getConfig();

    /**
     * 更新来源开关。
     *
     * @param config 键为来源键、值为开关
     */
    void updateConfig(Map<String, Boolean> config);

    /**
     * 列出内置推荐 MCP（自带可直连 transport，可一键安装）。
     *
     * @return 内置推荐条目
     */
    List<Map<String, Object>> listBuiltin();

    /**
     * 在已启用的在线来源中检索 MCP（真实第三方仓库引用，需手动配置传输）。
     *
     * @param keyword 关键词，可空
     * @return 在线引用条目（installable=false）
     */
    List<Map<String, Object>> searchOnline(String keyword);
}
