package com.chua.starter.device.support.adaptor.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.mapping.annotations.MappingAddress;
import com.chua.common.support.mapping.annotations.MappingBody;
import com.chua.common.support.mapping.annotations.MappingParam;
import com.chua.common.support.mapping.annotations.MappingRequest;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.client.pojo.HikAnFangOrgListResult;
import com.chua.starter.device.support.adaptor.client.pojo.HikYunYaoDeviceListResult;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

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
