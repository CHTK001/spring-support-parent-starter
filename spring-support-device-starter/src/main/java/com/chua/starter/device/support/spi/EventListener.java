package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceEvent;

@FunctionalInterface
public interface EventListener {
    void onEvent(DeviceEvent event);
}