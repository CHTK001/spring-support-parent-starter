package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模型设置 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiModelSettingMapper extends BaseMapper<SysAiModelSetting> {
}
