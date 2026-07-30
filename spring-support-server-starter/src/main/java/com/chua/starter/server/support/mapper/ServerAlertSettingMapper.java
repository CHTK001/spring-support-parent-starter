package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.ServerAlertSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServerAlertSettingMapper extends BaseMapper<ServerAlertSetting> {
}
