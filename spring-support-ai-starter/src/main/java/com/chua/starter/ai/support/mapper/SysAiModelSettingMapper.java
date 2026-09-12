package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import org.apache.ibatis.annotations.Mapper;

/**
   * AI 模型设置 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiModelSettingMapper extends BaseMapper<SysAiModelSetting> {
}
