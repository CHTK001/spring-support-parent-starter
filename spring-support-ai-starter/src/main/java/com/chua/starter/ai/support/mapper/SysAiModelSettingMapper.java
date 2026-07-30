package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAiModelSettingMapper extends BaseMapper<SysAiModelSetting> {
}
