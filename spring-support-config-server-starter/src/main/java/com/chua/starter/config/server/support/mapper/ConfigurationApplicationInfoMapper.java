package com.chua.starter.config.server.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.config.server.support.entity.ConfigurationApplicationInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author CH
 */
@Mapper
public interface ConfigurationApplicationInfoMapper extends BaseMapper<ConfigurationApplicationInfo> {
}