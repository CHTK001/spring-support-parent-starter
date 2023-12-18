package com.chua.starter.device.support.adaptor.factory.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.mapping.annotations.Url;
import com.chua.common.support.mapping.annotations.Body;
import com.chua.common.support.mapping.annotations.Param;
import com.chua.common.support.mapping.annotations.Query;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.factory.client.pojo.HikAnFangOrgListResult;
import com.chua.starter.device.support.adaptor.factory.client.pojo.HikYunYaoDeviceListResult;
import com.chua.starter.device.support.adaptor.pojo.AccessEventRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * @author CH
 */
@Url(invokeType = "hai_kang_yun_yao")
public interface HikYunYaoClient {
    /**
     * 查询组织列表v2
     * 接口说明
     * <p>
     * 根据不同的组织属性分页查询组织信息。
     * 查询组织列表接口可以根据组织唯一标识集、组织名称、组织状态这些查询条件来进行高级查询；若不指定查询条件，即全量获取所有的组织信息。返回结果分页展示。
     * 注：若指定多个查询条件，表示将这些查询条件进行“与”的组合后进行精确查询。
     * 根据“组织名称orgName”查询为模糊查询。
     * 根据父组织查询子孙组织，为便于构建组织树，会返回没有权限的父组织，通过available为false区分； 如果额外指定了其它字段，为在返回数据的基础上进一步过滤出符合条件的数据。
     *
     * @param pageNo     页码(1)
     * @param pageSize 分页数量 (1000)
     * @return 组织机构
     */
    @Query(value = "POST /api/resource/v2/org/advance/orgList", jsonPath = "$.data")
    HikAnFangOrgListResult orgList(int pageNo, int pageSize);

    /**
     * 设备分页查询
     * @param json json
     *  <code>
     *      [
     *        {
     * 		"key":"pageSize",
     * 		"option":"eq",
     * 		"value":300
     *    },{
     * 		"key":"pageNo",
     * 		"option":"eq",
     * 		"value":1
     *    },{
     * 		"key":"deviceCategory",
     * 		"option":"eq",
     * 		"value":"DecodeStitchControl"
     *    },{
     *                 "key":"projectId",
     * 		"option":"eq",
     * 		"value":1688929979961872
     *     }
     * ]
     *             </code>
     * @return
     */
    @Query(value = "POST /api/eits/v2/global/device/page", jsonPath = "$.data")
    HikYunYaoDeviceListResult devicePage(@Body String json);

    /**
     * 设备页面
     *
     * @param pageNo    页码(1)
     * @param pageSize  分页数量 (1000)
     * @param projectId 项目id
     * @return {@link HikYunYaoDeviceListResult}
     */
    default HikYunYaoDeviceListResult devicePage(int pageNo, int pageSize, String projectId) {
        if(StringUtils.isEmpty(projectId)) {
            return null;
        }
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(new JSONObject().fluentPut("key", "pageSize").fluentPut("option", "eq").fluentPut("value", pageSize));
        jsonArray.add(new JSONObject().fluentPut("key", "pageNo").fluentPut("option", "eq").fluentPut("value", pageNo));
        jsonArray.add(new JSONObject().fluentPut("key", "projectId").fluentPut("option", "eq").fluentPut("value", Long.valueOf(projectId)));
        return devicePage(jsonArray.toJSONString());
    }


    /**
     * 获取实时地址
     *
     * @param deviceImsi 设备imsi
     * @param projectId  项目id
     * @param channelNo  通道编号
     * @param expireTime 过期时间
     * @param protocol   协议
     * @return {@link String}
     */
    @Query("POST /api/eits/v1/global/live/address/get/by/deviceSerial")
    String getLiveAddress(String projectId, String deviceSerial,
                          @Param(value = "channelNo", defaultValue = "1") int channelNo,
                          @Param(value = "expireTime", defaultValue = "60") int expireTime,
                          @Param(value = "protocol", defaultValue = "2") int protocol
    );

    /**
     * 获取实时地址
     *
     * @param deviceSerial 设备imsi
     * @param projectId  项目id
     * @return {@link String}
     */
    default String getLiveAddress(String projectId, String deviceSerial ) {
        return getLiveAddress(projectId, deviceSerial, 1, 60, 2);
    }

    /**
     * 获取实时地址
     *
     * @param deviceSerial 设备imsi
     * @param projectId  项目id
     * @return {@link String}
     */
    default String getLiveAddress(String projectId, String deviceSerial , Integer channelNo) {
        return getLiveAddress(projectId, deviceSerial, channelNo, 43200, 2);
    }

    /**
     * 获取事件
     *
     * @param request 要求
     * @return {@link String}
     */
    @Query("POST /api/eits/aceventcs/v1/event/acs/person/page")
    String getEvent(@Body String request);

    /**
     * 获取事件
     *
     * @param request 要求
     * @return {@link String}
     */
    default String getEvent(AccessEventRequest request) {
        Map<String, Object> beanMap = new HashMap<>(BeanMap.create(request));
        beanMap.put("eventStartTime", DateUtils.format(request.getStartTime(), DateFormatConstant.ISO8601));
        beanMap.put("eventEndTime", DateUtils.format(request.getEndTime(), DateFormatConstant.ISO8601));
        beanMap.remove("eventType");
        return getEvent(Json.toJson(beanMap));
    }
}
