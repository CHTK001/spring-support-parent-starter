package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抓包会话 Mapper
 */
@Mapper
public interface MsTsharkSessionMapper extends BaseMapper<MsTsharkSession> {
}
