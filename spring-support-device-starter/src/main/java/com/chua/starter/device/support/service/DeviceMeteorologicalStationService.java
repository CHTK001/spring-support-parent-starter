package com.chua.starter.device.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.request.DataFilter;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceMeteorologicalStationEvent;

import java.util.List;

/**
 * 设备气象站服务
 *
 * @author CH
 * @since 2023/10/30
 */
public interface DeviceMeteorologicalStationService {


    /**
     * 注册事件
     *
     * @param event             事件
     * @param platformConnector 平台连接器
     * @param result            后果
     */
    void registerEvent(List<DeviceMeteorologicalStationEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result);

    /**
     * 分页
     *
     * @param deviceChannelPage 装置频道分页
     * @param dataFilter        数据过滤器
     * @return {@link Page}<{@link DeviceMeteorologicalStationEvent}>
     */
    Page<DeviceMeteorologicalStationEvent> page(Page<DeviceMeteorologicalStationEvent> deviceChannelPage, DataFilter dataFilter);
}
