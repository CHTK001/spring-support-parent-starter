package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话消息 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiMessageMapper extends BaseMapper<SysAiMessage> {
}
