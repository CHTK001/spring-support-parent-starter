package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 设置 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiSettingMapper extends BaseMapper<SysAiSetting> {
}
