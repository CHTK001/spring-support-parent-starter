package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟-世界事件时间线 Mapper
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface SysAiWorldEventMapper extends BaseMapper<SysAiWorldEvent> {
}