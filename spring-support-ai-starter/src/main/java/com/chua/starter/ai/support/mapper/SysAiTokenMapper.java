package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiToken;
import org.apache.ibatis.annotations.Mapper;

/**
* AI 令牌 映射器。
*
* @author CH
* @since 4.0.0
 */
@Mapper
public interface SysAiTokenMapper extends BaseMapper<SysAiToken> {
}
