package com.chua.starter.device.support.adaptor.event;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.device.support.adaptor.HikYunYaoAdaptor;
import com.chua.starter.device.support.adaptor.event.yunyao.EventHandler;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.adaptor.transit.AccessEventYunYaoTransit;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;
import com.chua.starter.device.support.entity.DeviceDataEvent;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 姚访问事件适配器
 *
 * @author CH
 * @since 2023/10/27
 */
@Group(value = "access_event", desc = "同步门禁事件", group = "service")
@Spi("HAI_KANG_YUN_YAO")
public class HikYunYaoAccessEventAdaptor
    extends HikYunYaoAdaptor
    implements AccessEventAdaptor {
    public HikYunYaoAccessEventAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<? extends DeviceDataEvent> getEvent(AccessEventRequest request) {
        request.setProjectId(deviceCloudPlatformConnector.getDeviceConnectorProjectCode());
        request.setProjectCode(deviceCloudPlatformConnector.getDeviceConnectorProjectId());
        EventHandler eventHandler = ServiceProvider.of(EventHandler.class).getNewExtension(request.getEventType());
        if(null == eventHandler) {
            throw new RuntimeException("事件不存在");
        }
        return eventHandler.getEvent(request, hikYunYaoClient);
    }
}
