package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.DeviceEvent;
/**
 * @作者 CH
 *
 * @author CH
 * @since 4.0.0.51
*/

@FunctionalInterface
public interface EventListener {
    /**
     * on事件。
     * @param event 事件
     */
    void onEvent(DeviceEvent event);
}
