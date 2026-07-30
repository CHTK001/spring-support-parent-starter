package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiModule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModuleMapper extends BaseMapper<SysAiModule> {
}
