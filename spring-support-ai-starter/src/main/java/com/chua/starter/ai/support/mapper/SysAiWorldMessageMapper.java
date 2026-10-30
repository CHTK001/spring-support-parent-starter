package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiWorldMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟-场景对话消息 Mapper
 *
 * @author CH
 */
@Mapper
public interface SysAiWorldMessageMapper extends BaseMapper<SysAiWorldMessage> {
}