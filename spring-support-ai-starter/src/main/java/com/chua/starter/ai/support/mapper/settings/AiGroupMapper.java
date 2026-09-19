package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.SysAiGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型分组 映射器
 *
 * @since 2026-08-03
 * @author CH
 */
@Mapper
public interface AiGroupMapper extends BaseMapper<SysAiGroup> {
}
