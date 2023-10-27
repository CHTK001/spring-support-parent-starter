package com.chua.starter.device.support.adaptor.event;

import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;

import java.util.List;

/**
 * 访问事件
 *
 * @author CH
 * @since 2023/10/27
 */
public interface AccessEventAdaptor {


    /**
     * 获取事件
     *
     * @param request 要求
     * @return {@link DeviceDataAccessEvent}
     */
    List<DeviceDataAccessEvent> getEvent(AccessEventRequest request);
}
