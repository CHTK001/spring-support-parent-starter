package com.chua.starter.appsystem.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.appsystem.support.entity.MsAppSystem;

import java.util.List;
import java.util.Map;

/**
 * 业务系统服务
 * <p>
 * 提供业务系统（立项）台账的增删改查、启用状态维护、主机池管理与池范围校验。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AppSystemService {

    /**
     * 新增业务系统
     *
     * @param system 业务系统
     * @return 结果
     */
    ReturnResult<MsAppSystem> create(MsAppSystem system);

    /**
     * 更新业务系统
     *
     * @param id     主键
     * @param system 业务系统
     * @return 结果
     */
    ReturnResult<MsAppSystem> update(Long id, MsAppSystem system);

    /**
     * 删除业务系统（仍存在下属部署项目时拒绝）
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Boolean> delete(Long id);

    /**
     * 业务系统详情
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<MsAppSystem> get(Long id);

    /**
     * 业务系统列表
     *
     * @param keyword 关键字（名称/编码），可为空
     * @param type    操作系统标签 LINUX/WINDOWS，可为空
     * @return 结果
     */
    ReturnResult<List<MsAppSystem>> query(String keyword, String type);

    /**
     * 更新启用状态
     *
     * @param id      主键
     * @param enabled 是否启用
     * @return 结果
     */
    ReturnResult<Boolean> updateEnabled(Long id, Boolean enabled);

    /**
     * 全量可选主机（供主机池选择）
     *
     * @return 结果（id/name/address/osType/online）
     */
    ReturnResult<List<Map<String, Object>>> hostOptions();

    /**
     * 某业务系统主机池内的主机明细
     *
     * @param id 业务系统主键
     * @return 结果（id/name/address/osType/online）
     */
    ReturnResult<List<Map<String, Object>>> poolHosts(Long id);

    /**
     * 校验主机是否落在业务系统主机池内
     * <p>
     * 由下游部署项目模块调用：项目只能部署到所属系统主机池内的主机，
     * 越界主机直接报错而不是静默剔除，否则用户会以为已经部署到了那台机器。
     * </p>
     *
     * @param appSystemId 业务系统标识
     * @param hostIds     待校验主机 标识
     * @return 校验通过时回传原 标识 列表
     */
    ReturnResult<List<Long>> assertInPool(Long appSystemId, List<Long> hostIds);
}
