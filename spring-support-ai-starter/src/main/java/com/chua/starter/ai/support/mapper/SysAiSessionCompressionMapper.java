package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话压缩上下文 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiSessionCompressionMapper extends BaseMapper<SysAiSessionCompression> {
}
