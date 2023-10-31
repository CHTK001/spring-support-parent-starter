package com.chua.starter.device.support.adaptor.factory.client;

import com.chua.common.support.mapping.annotations.MappingAddress;
import com.chua.common.support.mapping.annotations.MappingRequest;

/**
 * @author CH
 */
@MappingAddress(invokeType = "sen_nuo")
public interface SnNuoClient {


    /**
     * 获取事件
     *
     * @param deviceImsi 设备imsi
     * @return {@link String}
     */
    @MappingRequest("GET /json/{deviceImsi}.json")
    String getEvent(String deviceImsi);
}
