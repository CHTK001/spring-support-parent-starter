package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.SysAiSettingUsage;

import org.apache.ibatis.annotations.Mapper;

/**
   * 用量统计 映射器
 *
 * @since 2026-08-03
 * @author CH
 */
@Mapper
public interface AiUsageMapper extends BaseMapper<SysAiSettingUsage> {
}
