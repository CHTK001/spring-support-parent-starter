package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceWebhookReceipt;
import org.apache.ibatis.annotations.Mapper;

/**
 * webhook 入站幂等回执 映射器
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceWebhookReceiptMapper extends BaseMapper<DeviceWebhookReceipt> {
}
