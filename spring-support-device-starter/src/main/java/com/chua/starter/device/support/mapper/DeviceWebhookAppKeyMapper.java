package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceWebhookAppKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * webhook 应用凭据 映射器
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceWebhookAppKeyMapper extends BaseMapper<DeviceWebhookAppKey> {
}
