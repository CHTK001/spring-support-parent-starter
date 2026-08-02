package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用量统计 Mapper
 *
 * @author CH
 * @since 2026-08-03
 */
@Mapper
public interface AiUsageMapper extends BaseMapper<AiUsage> {
}
