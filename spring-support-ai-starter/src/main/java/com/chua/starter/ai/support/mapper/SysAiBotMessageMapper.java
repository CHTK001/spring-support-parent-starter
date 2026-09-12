package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiBotMessage;
import org.apache.ibatis.annotations.Mapper;

/**
* 微信 机器人 聊天消息记录 映射器。
*
* @author CH
* @since 4.0.0.42
 */
@Mapper
public interface SysAiBotMessageMapper extends BaseMapper<SysAiBotMessage> {
}