package com.chua.starter.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.server.support.entity.MsServerConfig;
import org.apache.ibatis.annotations.Mapper;

/**
* 服务器系统配置 映射器
* @author CH
* @since 4.0.0
 */
@Mapper
public interface MsServerConfigMapper extends BaseMapper<MsServerConfig> {
}
