package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据包 Mapper
 */
@Mapper
public interface MsTsharkPacketMapper extends BaseMapper<MsTsharkPacket> {
}
