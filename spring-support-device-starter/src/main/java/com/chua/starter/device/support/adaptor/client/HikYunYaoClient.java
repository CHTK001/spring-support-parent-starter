package com.chua.starter.device.support.adaptor.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.mapping.annotations.MappingAddress;
import com.chua.common.support.mapping.annotations.MappingBody;
import com.chua.common.support.mapping.annotations.MappingRequest;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.client.pojo.DeviceListResult;
import com.chua.starter.device.support.adaptor.client.pojo.OrgListResult;

/**
 * @author CH
 */
@MappingAddress(invokeType = "hai_kang_yun_yao")
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
    @MappingRequest(value = "POST /api/resource/v2/org/advance/orgList", jsonPath = "$.data")
    OrgListResult orgList(int pageNo, int pageSize);

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
    @MappingRequest(value = "POST /api/eits/v2/global/device/page", jsonPath = "$.data")
    DeviceListResult devicePage(@MappingBody String json);

    /**
     * 设备页面
     *
     * @param pageNo    页码(1)
     * @param pageSize  分页数量 (1000)
     * @param projectId 项目id
     * @return {@link DeviceListResult}
     */
    default DeviceListResult devicePage(int pageNo, int pageSize, String projectId) {
        if(StringUtils.isEmpty(projectId)) {
            return null;
        }
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(new JSONObject().fluentPut("key", "pageSize").fluentPut("option", "eq").fluentPut("value", pageSize));
        jsonArray.add(new JSONObject().fluentPut("key", "pageNo").fluentPut("option", "eq").fluentPut("value", pageNo));
        jsonArray.add(new JSONObject().fluentPut("key", "projectId").fluentPut("option", "eq").fluentPut("value", Long.valueOf(projectId)));
        return devicePage(jsonArray.toJSONString());
    }


}
