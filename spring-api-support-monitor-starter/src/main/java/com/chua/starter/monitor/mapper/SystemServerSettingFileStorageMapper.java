package com.chua.starter.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.entity.SystemServerSettingFileStorage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemServerSettingFileStorageMapper extends BaseMapper<SystemServerSettingFileStorage> {

    List<SystemServerSettingFileStorage> selectByServerId(@Param("serverId") Integer serverId);
}

