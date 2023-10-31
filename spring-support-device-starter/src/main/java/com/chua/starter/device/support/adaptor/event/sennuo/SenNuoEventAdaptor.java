package com.chua.starter.device.support.adaptor.event.sennuo;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.event.AccessEventAdaptor;
import com.chua.starter.device.support.adaptor.factory.sennuo.SenNuoAdaptor;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceDataEvent;
import com.chua.starter.device.support.entity.DeviceMeteorologicalStationEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 森诺设备适配器
 *
 * @author CH
 * @since 2023/10/30
 */
@Spi("sen_nuo")
@Group(value = "access_xxz_event", desc = "气象站数据同步", group = "device")
public class SenNuoEventAdaptor extends SenNuoAdaptor implements AccessEventAdaptor {

    public SenNuoEventAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<? extends DeviceDataEvent> getEvent(AccessEventRequest request) {
        String deviceSerial = request.getDeviceSerial();
        if(StringUtils.isBlank(deviceSerial) || request.getPageNo() != 1) {
            return Collections.emptyList();
        }
        String event = snNuoClient.getEvent(deviceSerial);
        if(StringUtils.isBlank(event) || event.startsWith("<html>")) {
            return Collections.emptyList();
        }
        Event parseObject = JSON.parseObject(event, Event.class);
        DeviceMeteorologicalStationEvent event1 = new DeviceMeteorologicalStationEvent();
        event1.setTime(DateUtils.toInstant(DateTime.of(parseObject.getDatetime()).toLocalDateTime()));
        List<Event.DataDTO> data = parseObject.getData();
        Map<String, Object> tpl = new HashMap<>(data.size());
        for (Event.DataDTO datum : data) {
            String code = datum.getCode();
            String s = mapping.get(code);
            tpl.put(s, datum.getValue());
        }

        BeanUtils.copyProperties(tpl, event1);
        event1.setDeviceImsi(deviceSerial);
        event1.setSensor(deviceSerial);
        return Collections.singletonList(event1);
    }


    @NoArgsConstructor
    @Data
    public static class Event {

        @JsonProperty("datetime")
        private String datetime;
        @JsonProperty("data")
        private List<DataDTO> data;
        @JsonProperty("id")
        private String id;
        @JsonProperty("did")
        private String did;

        @NoArgsConstructor
        @Data
        public static class DataDTO {
            @JsonProperty("unit")
            private String unit;
            @JsonProperty("code")
            private String code;
            @JsonProperty("name")
            private String name;
            @JsonProperty("value")
            private String value;
        }
    }
}
