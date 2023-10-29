package com.chua.starter.device.support.adaptor.event.yunyao;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.starter.device.support.adaptor.client.HikYunYaoClient;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.adaptor.transit.AccessEventYunYaoTransit;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;
import com.chua.starter.device.support.entity.DeviceDataEvent;

import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 访问事件处理程序
 *
 * @author CH
 * @since 2023/10/28
 */
@Spi("ACCESS")
public class AccessEventHandler implements EventHandler{
    @Override
    public List<? extends DeviceDataEvent> getEvent(AccessEventRequest request, HikYunYaoClient client) {
        String event = client.getEvent(request);
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
            deviceDataAccessEvent.setDeviceImsi(listDTO.getDevId());

            rs.add(deviceDataAccessEvent);
        }

        return rs;
    }
}
