package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceEvent;
/** @author CH */

@FunctionalInterface
public interface EventListener {
    void onEvent(DeviceEvent event);
}
