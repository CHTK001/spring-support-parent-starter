package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceSyncOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备变更推送队列 映射器
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceSyncOutboxMapper extends BaseMapper<DeviceSyncOutbox> {
}
