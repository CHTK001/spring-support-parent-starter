package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警规则 Mapper
 *
 * @author CH
 * @since 2026-08-03
 */
@Mapper
public interface AiAlertMapper extends BaseMapper<AiAlert> {
}
