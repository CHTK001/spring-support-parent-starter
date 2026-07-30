package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysAiAppKeyMapper extends BaseMapper<SysAiAppKey> {

    List<SysAiAppKey> selectByGroupId(@Param("groupId") Integer groupId);

    SysAiAppKey selectByAppKey(@Param("appKey") String appKey);
}
