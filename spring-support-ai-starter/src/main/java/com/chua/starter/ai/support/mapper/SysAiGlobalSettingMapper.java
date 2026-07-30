package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SysAiGlobalSettingMapper extends BaseMapper<SysAiGlobalSetting> {

    List<SysAiGlobalSetting> selectByGroup(@Param("group") String group);

    Optional<SysAiGlobalSetting> selectByGroupAndField(@Param("group") String group, @Param("field") String field);
}
