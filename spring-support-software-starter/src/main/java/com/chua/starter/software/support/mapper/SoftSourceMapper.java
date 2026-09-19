package com.chua.starter.software.support.mapper;

import com.chua.starter.mybatis.mapper.BaseMapperX;
import com.chua.starter.software.support.entity.SoftSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 软件源表 映射器
 *
 * @since 2026/7/25
 * @author CH
 */
@Mapper
public interface SoftSourceMapper extends BaseMapperX<SoftSource> {
}