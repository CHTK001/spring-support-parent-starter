package com.chua.starter.datapool.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.datapool.support.entity.MsDatasourceApi;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态接口定义 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface MsDatasourceApiMapper extends BaseMapper<MsDatasourceApi> {
}