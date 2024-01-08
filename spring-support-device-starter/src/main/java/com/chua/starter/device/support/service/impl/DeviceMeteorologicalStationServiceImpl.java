package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.protocol.Client;
import com.chua.common.support.protocol.OperateClient;
import com.chua.common.support.protocol.options.ClientOption;
import com.chua.common.support.request.DataFilter;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.influxdb.support.InfluxClient;
import com.chua.influxdb.support.InfluxOperateClient;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.adaptor.properties.InfluxProperties;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceMeteorologicalStationEvent;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceMeteorologicalStationService;
import org.influxdb.InfluxDB;
import org.jetbrains.annotations.NotNull;
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
    private InfluxOperateClient operateClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        influxClient = (InfluxClient) Client
                .newProvider("influx", BeanUtils.copyProperties(influxProperties, ClientOption.class));
        influxClient.connect(influxProperties.getUrl());
        this.operateClient = influxClient.createClient();
        try {
            operateClient.createDatabase(influxProperties.getDatabase());
        } catch (Exception ignored) {
        }
        try {
            operateClient.createRetentionPolicy(influxProperties.getDatabase(),
                    influxProperties.getRetentionPolicy(), 180, 1, true);
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
                operateClient.insert(deviceMeteorologicalStationEvent.getDeviceImsi(), deviceMeteorologicalStationEvent,
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
    public Page<DeviceMeteorologicalStationEvent> page(Page<DeviceMeteorologicalStationEvent> deviceChannelPage, @NotNull DataFilter dataFilter) {
        String toSql = dataFilter.toSql("deviceImsi", true);
        if(null == toSql) {
            throw new RuntimeException("请选择指定设备");
        }
        StringBuilder stringBuilder = new StringBuilder(toSql);
        stringBuilder.append(" ORDER BY time desc LIMIT ").append(deviceChannelPage.getSize()).append(" OFFSET ").append((deviceChannelPage.getCurrent() - 1) * deviceChannelPage.getSize());

        List<DeviceMeteorologicalStationEvent> query = operateClient
                .query(stringBuilder.toString(), DeviceMeteorologicalStationEvent.class);
        deviceChannelPage.setRecords(query);
        return deviceChannelPage;
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(influxClient);
    }
}
