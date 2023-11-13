package com.chua.starter.unified.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UnifiedMybatisMapper extends BaseMapper<UnifiedMybatis> {
}