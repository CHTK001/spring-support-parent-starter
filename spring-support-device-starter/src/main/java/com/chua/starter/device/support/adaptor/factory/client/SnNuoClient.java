package com.chua.starter.device.support.adaptor.factory.client;

import com.chua.common.support.mapping.annotations.Url;
import com.chua.common.support.mapping.annotations.Query;

/**
 * @author CH
 */
@Url(invokeType = "sen_nuo")
public interface SnNuoClient {


    /**
     * 获取事件
     *
     * @param deviceImsi 设备imsi
     * @return {@link String}
     */
    @Query("GET /json/{deviceImsi}.json")
    String getEvent(String deviceImsi);
}
