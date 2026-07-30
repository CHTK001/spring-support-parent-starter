package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.ServerService;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServerServiceMapper extends BaseMapper<ServerService> {
}
