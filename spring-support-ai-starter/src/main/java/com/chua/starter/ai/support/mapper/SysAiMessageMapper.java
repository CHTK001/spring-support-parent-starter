package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话消息 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiMessageMapper extends BaseMapper<SysAiMessage> {
}
