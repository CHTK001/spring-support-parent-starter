package com.chua.starter.device.support.service.impl;

import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.pojo.StaticResult;
import com.chua.starter.device.support.adaptor.properties.InfluxProperties;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceMeteorologicalStationEvent;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceMeteorologicalStationService;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.WriteApi;
import com.influxdb.client.internal.InfluxDBClientImpl;
import com.influxdb.client.write.Point;
import org.springframework.beans.BeanUtils;
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

    private String retentionPolicy;
    private InfluxDBClient influxDBClient;
    @Resource
    private InfluxProperties influxProperties;
    @Resource
    private DeviceLogService deviceLogService;

    @Override
    public void afterPropertiesSet() throws Exception {
        influxDBClient = new InfluxDBClientImpl(InfluxDBClientOptions.builder()
                .url(influxProperties.getUrl())
                .authenticate(StringUtils.defaultString(influxProperties.getUsername(), "root"), StringUtils.defaultString(influxProperties.getPassword(), "root1234").toCharArray())
                .build());
        influxDBClient.enableGzip();
    }

    @Override
    public void registerEvent(List<DeviceMeteorologicalStationEvent> event, DeviceCloudPlatformConnector platformConnector, StaticResult result) {
        if (CollectionUtils.isEmpty(event) || null == influxDBClient) {
            return;
        }
        String connectorId = platformConnector.getDeviceConnectorId() + "";
        result.addTotal(event.size());
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setDeviceLogFrom("同步门禁事件接口(页面)");
        deviceLog.setCreateTime(new Date());
        deviceLog.setDeviceLogType("SYNC(" + connectorId + ")");
        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            for (DeviceMeteorologicalStationEvent deviceMeteorologicalStationEvent : event) {
                try {
                    Point point = Point.measurement(deviceMeteorologicalStationEvent.getDeviceImsi())
                            .addTag("sensor", deviceMeteorologicalStationEvent.getSensor());

                    BeanMap beanMap = BeanMap.of(deviceMeteorologicalStationEvent);
                    beanMap.forEach((k, v) -> {
                        if(v == null) {
                            return;
                        }
                        if (v instanceof Number) {
                            point.addField(k, (Number) v);
                            return;
                        }

                        point.addField(k, Converter.convertIfNecessary(v, String.class));
                    });

                    writeApi.writePoint(point);
                    result.addSuccessTotal(1);
                } catch (Exception e) {
                    result.addFailureTotal(1);
                    deviceLog.setDeviceLogError(e.getLocalizedMessage());
                }
            }
            deviceLogService.save(deviceLog);
        } catch (Exception ignore) {
        }
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(influxDBClient);
    }
}
