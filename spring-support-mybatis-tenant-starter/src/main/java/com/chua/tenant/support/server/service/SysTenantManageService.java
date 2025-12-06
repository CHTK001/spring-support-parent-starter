package com.chua.tenant.support.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.entity.SysTenant;
import com.chua.tenant.support.pojo.SysTenantServiceBindV1Request;
import com.chua.tenant.support.pojo.SysTenantSyncV1Request;

/**
 * 租户管理服务接口
 * <p>
 * 提供租户的增删改查、服务绑定、数据同步等功能
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface SysTenantManageService extends IService<SysTenant> {

    /**
     * 分页查询租户
     *
     * @param query     分页参数
     * @param sysTenant 查询条件
     * @return 分页结果
     */
    IPage<SysTenant> pageForTenant(Query<SysTenant> query, SysTenant sysTenant);

    /**
     * 保存租户
     *
     * @param sysTenant 租户信息
     * @return 保存结果
     */
    ReturnResult<SysTenant> saveForTenant(SysTenant sysTenant);

    /**
     * 更新租户
     *
     * @param sysTenant 租户信息
     * @return 更新结果
     */
    ReturnResult<Boolean> updateForTenant(SysTenant sysTenant);

    /**
     * 删除租户
     *
     * @param sysServiceId 租户ID
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteForTenant(Long sysServiceId);

    /**
     * 绑定租户服务
     *
     * @param request 绑定请求
     * @return 绑定结果
     */
    ReturnResult<Boolean> bindTenantService(SysTenantServiceBindV1Request request);

    /**
     * 同步租户数据
     *
     * @param request 同步请求
     * @return 同步结果
     */
    ReturnResult<Boolean> syncTenantData(SysTenantSyncV1Request request);
}
