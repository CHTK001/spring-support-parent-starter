package com.chua.starter.device.support.service.impl;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.protocol.client.ClientOption;
import com.chua.common.support.protocol.client.ClientProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.influxdb.support.InfluxClient;
import com.chua.influxdb.support.InfluxClientProvider;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.adaptor.properties.InfluxProperties;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceMeteorologicalStationEvent;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceMeteorologicalStationService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 设备气象站服务impl
 *
 * @author CH
 * @since 2023/10/30
 */
@Service
@EnableConfigurationProperties(InfluxProperties.class)
public class DeviceMeteorologicalStationServiceImpl implements DeviceMeteorologicalStationService, InitializingBean, DisposableBean {

    private InfluxClient influxClient;
    @Resource
    private InfluxProperties influxProperties;
    @Resource
    private DeviceLogService deviceLogService;

    @Override
    public void afterPropertiesSet() throws Exception {
        influxClient = (InfluxClient) ClientProvider.newProvider("influx", BeanUtils.copyProperties(influxProperties, ClientOption.class)).create();
        influxClient.connect(influxProperties.getUrl());
        try {
            influxClient.create("createDatabase")
                    .with(influxProperties.getDatabase())
                    .execute();
        } catch (Exception ignored) {
        }
        try {
            influxClient.create("createRetentionPolicy")
                    .with("retention")
                    .with(influxProperties.getDatabase())
                    .with("180d")
                    .with(1, int.class)
                    .with(true, boolean.class)
                    .execute();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void registerEvent(List<DeviceMeteorologicalStationEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result) {
        if (CollectionUtils.isEmpty(event) || null == influxClient) {
            return;
        }
        String connectorId = platformConnector.getDeviceConnectorId() + "";
        result.addTotal(event.size());
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceLogFrom("同步气象站"+ platformConnector.getDeviceConnectorName() +"事件接口(页面)");
        deviceLog.setCreateTime(new Date());
        deviceLog.setDeviceLogType("SYNC(" + connectorId + ")");
        for (DeviceMeteorologicalStationEvent deviceMeteorologicalStationEvent : event) {
            try {
                influxClient.write(deviceMeteorologicalStationEvent.getDeviceImsi(), deviceMeteorologicalStationEvent,
                        ImmutableBuilder.builderOfStringMap(String.class)
                                .put("sensor", deviceMeteorologicalStationEvent.getSensor())
                                .build()
                );
                result.addSuccessTotal(1);
            } catch (Exception e) {
                result.addFailureTotal(1);
                deviceLog.setDeviceLogError(e.getLocalizedMessage());
            }
        }
        deviceLogService.save(deviceLog);
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(influxClient);
    }
}
