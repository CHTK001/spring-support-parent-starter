package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSessionSubAgent;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话子代理运行记录 映射器。
 *
 * @since 4.0.0.52
 * @author CH
 */
@Mapper
public interface SysAiSessionSubAgentMapper extends BaseMapper<SysAiSessionSubAgent> {
}
