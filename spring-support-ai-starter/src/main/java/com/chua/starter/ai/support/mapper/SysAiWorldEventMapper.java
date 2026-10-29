package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟人生-世界事件时间线 Mapper
 *
 * @author CH
 */
@Mapper
public interface SysAiWorldEventMapper extends BaseMapper<SysAiWorldEvent> {
}