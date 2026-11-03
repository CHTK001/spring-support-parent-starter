package com.chua.starter.channel.tempaccount.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.channel.tempaccount.store.SysAiChannelTempMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 临时身份消息 映射器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface SysAiChannelTempMessageMapper extends BaseMapper<SysAiChannelTempMessage> {
}
