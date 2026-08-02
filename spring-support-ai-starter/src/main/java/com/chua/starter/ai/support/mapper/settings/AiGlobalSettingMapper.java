package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiGlobalSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局设置 Mapper
 *
 * @author CH
 * @since 2026-08-03
 */
@Mapper
public interface AiGlobalSettingMapper extends BaseMapper<AiGlobalSetting> {
}
