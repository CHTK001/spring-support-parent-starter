package com.chua.starter.device.support.adaptor.event;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.starter.device.support.adaptor.HikYunYaoAdaptor;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.adaptor.transit.AccessEventYunYaoTransit;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;

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
    public List<DeviceDataAccessEvent> getEvent(AccessEventRequest request) {
        request.setProjectId(deviceCloudPlatformConnector.getDeviceConnectorProjectCode());
        request.setProjectCode(deviceCloudPlatformConnector.getDeviceConnectorProjectId());
        String event = hikYunYaoClient.getEvent(request);
        AccessEventYunYaoTransit accessEventYunYaoTransit = JSON.parseObject(event, AccessEventYunYaoTransit.class);
        if(null == accessEventYunYaoTransit) {
            return Collections.emptyList();
        }

        if(!"200".equalsIgnoreCase(accessEventYunYaoTransit.getCode())) {
            throw new RuntimeException(accessEventYunYaoTransit.getMsg());
        }

        AccessEventYunYaoTransit.DataDTO data = accessEventYunYaoTransit.getData();
        List<AccessEventYunYaoTransit.DataDTO.ListDTO> list = data.getList();
        List<DeviceDataAccessEvent> rs = new LinkedList<>();
        for (AccessEventYunYaoTransit.DataDTO.ListDTO listDTO : list) {
            DeviceDataAccessEvent deviceDataAccessEvent = new DeviceDataAccessEvent();
            deviceDataAccessEvent.setDeviceDataCard(listDTO.getCardNo());
            deviceDataAccessEvent.setDeviceDataDataId(listDTO.getEventId());
            deviceDataAccessEvent.setDeviceDataEventCode(listDTO.getEventCode());
            deviceDataAccessEvent.setDeviceDataCert(listDTO.getCertNum());
            deviceDataAccessEvent.setDeviceDataEventInOrOut(listDTO.getInOrOut() + "");
            try {
                deviceDataAccessEvent.setDeviceDataEventTime(DateUtils.parseDate(listDTO.getEventTime()));
            } catch (ParseException ignored) {
            }
            deviceDataAccessEvent.setDeviceDataEventCode(listDTO.getEventCode());
            deviceDataAccessEvent.setDeviceDataEventCodeLabel(listDTO.getEventCodeStr());
            deviceDataAccessEvent.setDeviceDataEventType(listDTO.getEventType() + "");
            deviceDataAccessEvent.setDeviceDataFaceUrl(listDTO.getFaceUrl());
            deviceDataAccessEvent.setDeviceDataOrgId(listDTO.getOrgId());
            deviceDataAccessEvent.setDeviceDataOrgPathName(listDTO.getOrgPathName());
            deviceDataAccessEvent.setDeviceDataPersionGroupName(listDTO.getPersonGroupNames());
            deviceDataAccessEvent.setDeviceDataPersionName(listDTO.getPersonName());
            deviceDataAccessEvent.setDeviceDataPersionType(listDTO.getPersonType());
            deviceDataAccessEvent.setDeviceDataPersonNum(listDTO.getPersonNum());
            deviceDataAccessEvent.setDeviceDataPersonId(listDTO.getPersonId());
            deviceDataAccessEvent.setDeviceDataPhone(listDTO.getPhone());
            deviceDataAccessEvent.setDeviceDataPicUrl(listDTO.getPicUrl());
            deviceDataAccessEvent.setDeviceDataTemperature(listDTO.getTemperatureStr());
            deviceDataAccessEvent.setDeviceName(listDTO.getDevName());
            deviceDataAccessEvent.setDeviceId(listDTO.getDevId());

            rs.add(deviceDataAccessEvent);
        }

        return rs;
    }
}
