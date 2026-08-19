package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警规则 Mapper
 *
 * @since 2026-08-03
 * @author CH
 */
@Mapper
public interface AiAlertMapper extends BaseMapper<AiAlert> {
}
