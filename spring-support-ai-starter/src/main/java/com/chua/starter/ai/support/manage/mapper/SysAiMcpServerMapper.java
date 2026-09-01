package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiMcpServer;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 服务器档案 {@code sys_ai_mcp_server} 的 Mapper。
 *
 * <p>继承 {@link BaseMapper}，提供标准 CRUD（MyBatis-Plus）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiMcpServerMapper extends BaseMapper<SysAiMcpServer> {
}
