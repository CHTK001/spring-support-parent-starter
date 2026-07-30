package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAiSettingMapper extends BaseMapper<SysAiSetting> {
}
