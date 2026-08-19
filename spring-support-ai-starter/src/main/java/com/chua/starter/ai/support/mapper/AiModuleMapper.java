package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiModule;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模块 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface AiModuleMapper extends BaseMapper<SysAiModule> {
}
