package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiTerminalSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 终端会话 Mapper，对应表 {@code sys_ai_terminal_session}。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Mapper
public interface SysAiTerminalSessionMapper extends BaseMapper<SysAiTerminalSession> {
}
