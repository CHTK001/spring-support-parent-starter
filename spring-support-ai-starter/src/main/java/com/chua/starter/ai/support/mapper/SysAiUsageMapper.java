package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 用量 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiUsageMapper extends BaseMapper<SysAiUsage> {
}
