package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.MsServerProjectOpLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目操作日志 Mapper。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Mapper
public interface MsServerProjectOpLogMapper extends BaseMapper<MsServerProjectOpLog> {
}
