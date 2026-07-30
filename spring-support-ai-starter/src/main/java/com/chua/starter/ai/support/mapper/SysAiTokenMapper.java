package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 令牌 Mapper。
 *
 * @author CH
 */
@Mapper
public interface SysAiTokenMapper extends BaseMapper<SysAiToken> {
}
