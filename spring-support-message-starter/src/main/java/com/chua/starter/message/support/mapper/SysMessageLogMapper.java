package com.chua.starter.message.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.message.support.entity.SysMessageLog;
import org.apache.ibatis.annotations.Mapper;

/**
   * 消息推送日志 映射器
 *
 * @since 2026/08/03
 * @author CH
 */
@Mapper
public interface SysMessageLogMapper extends BaseMapper<SysMessageLog> {
}
