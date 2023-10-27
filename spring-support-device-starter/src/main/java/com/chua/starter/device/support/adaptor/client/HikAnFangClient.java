package com.chua.starter.device.support.adaptor.client;

import com.chua.common.support.mapping.annotations.MappingAddress;
import com.chua.common.support.mapping.annotations.MappingRequest;
import com.chua.starter.device.support.adaptor.client.pojo.HikAnFangDeviceListResult;
import com.chua.starter.device.support.adaptor.client.pojo.HikAnFangOrgListResult;
import com.chua.starter.device.support.adaptor.client.pojo.HikYunYaoDeviceListResult;

/**
 * @author CH
 */
@MappingAddress(invokeType = "hai_kang_an_fang")
public interface HikAnFangClient {
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
    HikAnFangOrgListResult orgList(int pageNo, int pageSize);


    /**
     * 设备页面
     *
     * @param pageNo    页码(1)
     * @param pageSize  分页数量 (1000)
     * @return {@link HikYunYaoDeviceListResult}
     */
    @MappingRequest(value = "POST /api/resource/v2/encodeDevice/search", jsonPath = "$.data")
    HikAnFangDeviceListResult devicePage(int pageNo, int pageSize);


}
