package com.chua.starter.device.support.adaptor.event.haikang;

import com.chua.starter.device.support.adaptor.client.HikYunYaoClient;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.entity.DeviceDataEvent;

import java.util.List;

/**
 * 事件处理程序
 *
 * @author CH
 * @since 2023/10/28
 */
public interface EventHandler {

    /**
     * 获取事件
     *
     * @param request 要求
     * @param client  客户
     * @return {@link List}<{@link DeviceDataEvent}>
     */
    List<? extends DeviceDataEvent> getEvent(AccessEventRequest request, HikYunYaoClient client);
}
