package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.ServerServiceOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServerServiceOperationLogMapper extends BaseMapper<ServerServiceOperationLog> {
}
