package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DevicePerson;
import com.chua.starter.mybatis.entity.PageResult;

import java.util.List;

/**
 * 厂商人员台账服务。
 *
 * <p>只维护本地 {@code device_person} 台账，不直接调用厂商接口；
 * 与厂商侧的同步由 {@code DeviceSyncService} 负责。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DevicePersonService {

    /**
     * 新增人员。
     *
     * @param person 人员
     * @return 入库后的人员
     */
    DevicePerson addPerson(DevicePerson person);

    /**
     * 修改人员。
     *
     * @param id     主键
     * @param person 人员
     */
    void updatePerson(String id, DevicePerson person);

    /**
     * 删除人员（逻辑删除）。
     *
     * @param id 主键
     */
    void deletePerson(String id);

    /**
     * 查询单个人员。
     *
     * @param id 主键
     * @return 人员
     */
    DevicePerson getPerson(String id);

    /**
     * 查询人员列表。
     *
     * @param vendor  厂商标识，可选
     * @param keyword 姓名/手机号/厂商人员标识关键字，可选
     * @return 人员列表
     */
    List<DevicePerson> listPersons(String vendor, String keyword);

    /**
     * 分页查询人员。
     *
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @param vendor  厂商标识，可选
     * @param keyword 关键字，可选
     * @return 分页结果
     */
    PageResult<DevicePerson> pagePersons(int page, int size, String vendor, String keyword);
}
